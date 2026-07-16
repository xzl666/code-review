package com.cmbchina.codereview.infrastructure.ai;

import com.cmbchina.codereview.application.service.SystemConfigAppService;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.AiSkillEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DeepSeekClient {

    private static final String REVIEW_FUNCTION_NAME = "submit_review_issues";

    private static final String REVIEW_FUNCTION_DESCRIPTION =
        "Submit structured code review issues found by the AI skill. Return an empty issues array when no actionable issue exists.";

    private static final String REVIEW_PARAMETERS_SCHEMA = "{\n"
        + "  \"type\": \"object\",\n"
        + "  \"properties\": {\n"
        + "    \"issues\": {\n"
        + "      \"type\": \"array\",\n"
        + "      \"items\": {\n"
        + "        \"type\": \"object\",\n"
        + "        \"properties\": {\n"
        + "          \"issueType\": { \"type\": \"string\" },\n"
        + "          \"severity\": { \"type\": \"string\", \"enum\": [\"CRITICAL\", \"HIGH\", \"MEDIUM\", \"LOW\"] },\n"
        + "          \"filePath\": { \"type\": \"string\" },\n"
        + "          \"startLine\": { \"type\": \"integer\" },\n"
        + "          \"endLine\": { \"type\": \"integer\" },\n"
        + "          \"summary\": { \"type\": \"string\" },\n"
        + "          \"detail\": { \"type\": \"string\" },\n"
        + "          \"suggestion\": { \"type\": \"string\" },\n"
        + "          \"codeSnippet\": { \"type\": \"string\" }\n"
        + "        },\n"
        + "        \"required\": [\"issueType\", \"severity\", \"filePath\", \"summary\", \"detail\", \"suggestion\"]\n"
        + "      }\n"
        + "    }\n"
        + "  },\n"
        + "  \"required\": [\"issues\"]\n"
        + "}";

    private final DeepSeekProperties properties;

    private final SystemConfigAppService systemConfigAppService;

    private final ObjectMapper objectMapper;

    public DeepSeekClient(DeepSeekProperties properties,
                          SystemConfigAppService systemConfigAppService,
                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.systemConfigAppService = systemConfigAppService;
        this.objectMapper = objectMapper;
    }

    public String review(Project project, ReviewRuleEntity rule, AiSkillEntity skill, DiffChunk chunk, String branch, Integer reviewDays) {
        String apiKey = systemConfigAppService.getDeepSeekApiKey(properties.getApiKey());
        String url = normalizeChatCompletionsUrl(systemConfigAppService.getDeepSeekUrl(properties.getUrl()));
        String model = systemConfigAppService.getDeepSeekModel(properties.getModel());
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek API key is not configured");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(requestBody(project, rule, skill, chunk, branch, reviewDays, model));
            HttpResult response = postJson(url, apiKey, requestBody);
            if (response.statusCode < 200 || response.statusCode >= 300) {
                throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek request failed: HTTP " + response.statusCode + "; " + response.body);
            }
            return extractJsonPayload(extractArguments(response.body));
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek request error: " + exception.getMessage());
        }
    }

    public String repairReviewArguments(String invalidArguments,
                                        String parseError,
                                        Project project,
                                        ReviewRuleEntity rule,
                                        AiSkillEntity skill,
                                        DiffChunk chunk,
                                        String branch,
                                        Integer reviewDays) {
        String apiKey = systemConfigAppService.getDeepSeekApiKey(properties.getApiKey());
        String url = normalizeChatCompletionsUrl(systemConfigAppService.getDeepSeekUrl(properties.getUrl()));
        String model = systemConfigAppService.getDeepSeekModel(properties.getModel());
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek API key is not configured");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(
                repairRequestBody(invalidArguments, parseError, project, rule, skill, chunk, branch, reviewDays, model)
            );
            HttpResult response = postJson(url, apiKey, requestBody);
            if (response.statusCode < 200 || response.statusCode >= 300) {
                throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek repair request failed: HTTP " + response.statusCode + "; " + response.body);
            }
            return extractJsonPayload(extractArguments(response.body));
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek repair request error: " + exception.getMessage());
        }
    }

    private Map<String, Object> requestBody(Project project,
                                            ReviewRuleEntity rule,
                                            AiSkillEntity skill,
                                            DiffChunk chunk,
                                            String branch,
                                            Integer reviewDays,
                                            String model) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages(project, rule, skill, chunk, branch, reviewDays));
        body.put("tools", tools(skill));
        body.put("temperature", 0);
        body.put("max_tokens", maxTokens());
        return body;
    }

    private Map<String, Object> repairRequestBody(String invalidArguments,
                                                  String parseError,
                                                  Project project,
                                                  ReviewRuleEntity rule,
                                                  AiSkillEntity skill,
                                                  DiffChunk chunk,
                                                  String branch,
                                                  Integer reviewDays,
                                                  String model) throws Exception {
        Map<String, Object> body = requestBody(project, rule, skill, chunk, branch, reviewDays, model);
        body.put("messages", repairMessages(invalidArguments, parseError, project, rule, skill, chunk, branch, reviewDays));
        body.put("temperature", 0);
        return body;
    }

    private List<Map<String, String>> messages(Project project,
                                               ReviewRuleEntity rule,
                                               AiSkillEntity skill,
                                               DiffChunk chunk,
                                               String branch,
                                               Integer reviewDays) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", "You are a strict code review assistant. Return only structured issues through the provided function. Descriptive fields such as summary, detail and suggestion must be written in Chinese. Every code symbol, annotation and snippet must be placed inside JSON string fields with valid escaping. If there are no issues, call the function with {\"issues\":[]}."));
        String prompt = renderPrompt(rule.getPromptTemplate(), project, skill, chunk, branch, reviewDays);
        messages.add(message("user", prompt));
        return messages;
    }

    private List<Map<String, String>> repairMessages(String invalidArguments,
                                                     String parseError,
                                                     Project project,
                                                     ReviewRuleEntity rule,
                                                     AiSkillEntity skill,
                                                     DiffChunk chunk,
                                                     String branch,
                                                     Integer reviewDays) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", "You repair malformed function arguments for a code review tool. Call only the provided function. Do not output Markdown or explanatory text. Produce valid JSON arguments that match the schema. Preserve useful review findings from the malformed input. If fields are missing, infer reasonable values from the diff, file path, rule severity and schema. Descriptive fields must be Chinese."));
        String prompt = "The previous function arguments were invalid JSON.\n"
            + "Parse error: " + value(parseError) + "\n"
            + "Project: " + value(project.getProjectName()) + "\n"
            + "ProjectType: " + value(project.getProjectType()) + "\n"
            + "Branch: " + value(branch) + "\n"
            + "ReviewDays: " + value(reviewDays) + "\n"
            + "DefaultFilePath: " + value(chunk.getFilePath()) + "\n"
            + "DefaultSeverity: " + value(rule.getSeverity()) + "\n"
            + "FunctionName: " + REVIEW_FUNCTION_NAME + "\n"
            + "SkillProjectType: " + value(skill.getProjectType()) + "\n"
            + "SkillRuleMatchingEnabled: " + value(skill.getRuleMatchingEnabled()) + "\n"
            + "SkillMatchRules:\n" + value(skill.getMatchRules()) + "\n"
            + "SkillReviewGuidelines:\n" + value(skill.getReviewGuidelines()) + "\n"
            + "FunctionSchema:\n" + objectMapper.writeValueAsString(strictParametersSchema(objectMapper.readTree(REVIEW_PARAMETERS_SCHEMA))) + "\n"
            + "ChangedNewLines:\n" + changedNewLines(chunk) + "\n"
            + "MalformedArguments:\n" + value(invalidArguments) + "\n"
            + "OriginalDiff:\n" + value(chunk.getContent());
        messages.add(message("user", prompt));
        return messages;
    }

    private String renderPrompt(String template, Project project, AiSkillEntity skill, DiffChunk chunk, String branch, Integer reviewDays) {
        boolean templateContainsDiff = StringUtils.hasText(template) && template.contains("${diffContent}");
        String prompt = StringUtils.hasText(template) ? template : "Review the following git diff and report code issues.";
        prompt = prompt.replace("${projectName}", value(project.getProjectName()));
        prompt = prompt.replace("${projectType}", value(project.getProjectType()));
        prompt = prompt.replace("${branch}", value(branch));
        prompt = prompt.replace("${reviewDays}", String.valueOf(reviewDays));
        prompt = prompt.replace("${diffContent}", value(chunk.getContent()));
        StringBuilder builder = new StringBuilder(prompt)
            .append("\n\nUse Chinese for summary, detail, suggestion and other descriptive fields.")
            .append("\nReturn only through the provided function. Do not output Markdown, code fences, explanations or plain text.")
            .append("\nAll code, annotations such as @Valid, quotes, backslashes and newlines must be valid JSON string values with correct escaping.")
            .append("\nSkillProjectType: ").append(value(skill.getProjectType())).append(". SkillRuleMatchingEnabled: ").append(value(skill.getRuleMatchingEnabled())).append(".")
            .append("\nWhen SkillMatchRules are provided, treat them as review scope hints and focus on issues relevant to those rules.")
            .append("\nIf a line range can be located, fill startLine and endLine. If it is a single line issue, use the same value for both fields.")
            .append("\nOnly report issues on changed new-file lines listed in ChangedNewLines. Do not report issues that appear only in unchanged context lines.")
            .append("\nstartLine and endLine must be actual new-file line numbers from ChangedNewLines. If no changed line is relevant, return no issue.")
            .append("\nIf there are no issues, return {\"issues\":[]} through the function.")
            .append("\n\nFile: ").append(chunk.getFilePath())
            .append("\nChunkIndex: ").append(chunk.getChunkIndex())
            .append("\nOldStartLine: ").append(value(chunk.getOldStartLine()))
            .append("\nNewStartLine: ").append(value(chunk.getNewStartLine()))
            .append("\nSkillMatchRules:\n").append(value(skill.getMatchRules()))
            .append("\nSkillReviewGuidelines:\n").append(value(skill.getReviewGuidelines()))
            .append("\nChangedNewLines:\n").append(changedNewLines(chunk));
        if (!templateContainsDiff) {
            builder.append("\nDiff:\n").append(chunk.getContent());
        }
        return builder.toString();
    }

    private String renderPrompt(String template, Project project, DiffChunk chunk, String branch, Integer reviewDays) {
        AiSkillEntity skill = new AiSkillEntity();
        skill.setProjectType("ALL");
        skill.setRuleMatchingEnabled(0);
        skill.setReviewGuidelines("");
        return renderPrompt(template, project, skill, chunk, branch, reviewDays);
    }

    private String changedNewLines(DiffChunk chunk) {
        if (chunk == null || !StringUtils.hasText(chunk.getContent())) {
            return "";
        }
        int currentNewLine = chunk.getNewStartLine() == null ? 0 : chunk.getNewStartLine();
        StringBuilder builder = new StringBuilder();
        for (String line : chunk.getContent().split("\\R", -1)) {
            if (line.startsWith("@@")) {
                currentNewLine = parseNewStartLine(line, currentNewLine);
                continue;
            }
            if (line.startsWith("---") || line.startsWith("+++")) {
                continue;
            }
            if (line.startsWith("-")) {
                continue;
            }
            if (line.startsWith("+")) {
                builder.append(currentNewLine).append(": ").append(line).append('\n');
            }
            if (!line.startsWith("\\ No newline at end of file")) {
                currentNewLine++;
            }
        }
        return builder.toString();
    }

    private int parseNewStartLine(String hunkHeader, int fallback) {
        int plusIndex = hunkHeader.indexOf('+');
        if (plusIndex < 0) {
            return fallback;
        }
        int index = plusIndex + 1;
        StringBuilder number = new StringBuilder();
        while (index < hunkHeader.length() && Character.isDigit(hunkHeader.charAt(index))) {
            number.append(hunkHeader.charAt(index));
            index++;
        }
        return number.length() == 0 ? fallback : Integer.parseInt(number.toString());
    }

    private List<Map<String, Object>> tools(AiSkillEntity skill) throws Exception {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", REVIEW_FUNCTION_NAME);
        function.put("description", REVIEW_FUNCTION_DESCRIPTION);
        function.put("parameters", strictParametersSchema(objectMapper.readTree(REVIEW_PARAMETERS_SCHEMA)));
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool);
        return tools;
    }

    private JsonNode strictParametersSchema(JsonNode schema) {
        JsonNode copy = schema.deepCopy();
        addStrictObjectRules(copy);
        return copy;
    }

    private void addStrictObjectRules(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode type = object.get("type");
            if (type != null && "object".equals(type.asText()) && !object.has("additionalProperties")) {
                object.put("additionalProperties", false);
            }
            JsonNode properties = object.get("properties");
            if (properties != null && properties.isObject()) {
                properties.fields().forEachRemaining(entry -> addStrictObjectRules(entry.getValue()));
            }
            addStrictObjectRules(object.get("items"));
            JsonNode anyOf = object.get("anyOf");
            if (anyOf != null && anyOf.isArray()) {
                anyOf.forEach(this::addStrictObjectRules);
            }
            JsonNode oneOf = object.get("oneOf");
            if (oneOf != null && oneOf.isArray()) {
                oneOf.forEach(this::addStrictObjectRules);
            }
        } else if (node.isArray()) {
            node.forEach(this::addStrictObjectRules);
        }
    }

    private String extractArguments(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode message = root.path("choices").path(0).path("message");
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray() && toolCalls.size() > 0) {
            JsonNode arguments = toolCalls.path(0).path("function").path("arguments");
            if (!arguments.isMissingNode() && !arguments.isNull()) {
                return arguments.isTextual() ? arguments.asText() : arguments.toString();
            }
        }
        JsonNode functionCallArguments = message.path("function_call").path("arguments");
        if (!functionCallArguments.isMissingNode() && !functionCallArguments.isNull()) {
            return functionCallArguments.isTextual() ? functionCallArguments.asText() : functionCallArguments.toString();
        }
        JsonNode content = message.path("content");
        if (!content.isMissingNode() && !content.isNull()) {
            return content.isTextual() ? content.asText() : content.toString();
        }
        throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek response has no function arguments");
    }

    private String extractJsonPayload(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                return trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        int objectStart = trimmed.indexOf('{');
        int arrayStart = trimmed.indexOf('[');
        int start;
        if (objectStart < 0) {
            start = arrayStart;
        } else if (arrayStart < 0) {
            start = objectStart;
        } else {
            start = Math.min(objectStart, arrayStart);
        }
        int objectEnd = trimmed.lastIndexOf('}');
        int arrayEnd = trimmed.lastIndexOf(']');
        int end = Math.max(objectEnd, arrayEnd);
        if (start >= 0 && end >= start) {
            return trimmed.substring(start, end + 1).trim();
        }
        return trimmed;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String value(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeChatCompletionsUrl(String url) {
        if (!StringUtils.hasText(url) || url.contains("/chat/completions")) {
            return url;
        }
        String trimmed = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        return trimmed + "/v1/chat/completions";
    }

    private HttpResult postJson(String url, String apiKey, String requestBody) throws Exception {
        Duration timeout = Duration.ofSeconds(timeoutSeconds());
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            logAiDebug(url, requestBody, response.statusCode(), response.body(), null);
            return new HttpResult(response.statusCode(), response.body());
        } catch (Exception exception) {
            logAiDebug(url, requestBody, null, null, exception);
            throw exception;
        }
    }

    private void logAiDebug(String url, String requestBody, Integer statusCode, String responseBody, Exception exception) {
        if (!Boolean.TRUE.equals(properties.getDebugLogEnabled())) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestAddress", url);
            payload.put("prompt", promptFromRequestBody(requestBody));
            payload.put("requestBody", objectMapper.readTree(requestBody));
            payload.put("responseStatus", statusCode);
            payload.put("responseContent", parseJsonOrText(responseBody));
            if (exception != null) {
                payload.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            log.info("AI request debug log: {}", objectMapper.writeValueAsString(payload));
        } catch (Exception logException) {
            log.warn("AI request debug log failed: {}", logException.getMessage());
        }
    }

    private List<String> promptFromRequestBody(String requestBody) throws Exception {
        List<String> prompts = new ArrayList<>();
        JsonNode messages = objectMapper.readTree(requestBody).path("messages");
        if (messages.isArray()) {
            for (JsonNode message : messages) {
                String role = message.path("role").asText("");
                String content = message.path("content").asText("");
                prompts.add(role + ": " + content);
            }
        }
        return prompts;
    }

    private Object parseJsonOrText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return value;
        }
    }

    private int timeoutSeconds() {
        Integer timeoutSeconds = properties.getTimeoutSeconds();
        return timeoutSeconds == null || timeoutSeconds < 1 ? 60 : timeoutSeconds;
    }

    private int maxTokens() {
        Integer maxTokens = properties.getMaxTokens();
        return maxTokens == null || maxTokens < 1 ? 2048 : maxTokens;
    }

    private static class HttpResult {
        private final int statusCode;
        private final String body;

        private HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
