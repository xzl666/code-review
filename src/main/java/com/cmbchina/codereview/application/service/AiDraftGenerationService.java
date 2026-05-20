package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.infrastructure.ai.DeepSeekProperties;
import com.cmbchina.codereview.interfaces.dto.request.AiGenerateScriptRequest;
import com.cmbchina.codereview.interfaces.dto.request.AiGenerateSkillRequest;
import com.cmbchina.codereview.interfaces.dto.response.AiGeneratedScriptResponse;
import com.cmbchina.codereview.interfaces.dto.response.AiGeneratedSkillResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class AiDraftGenerationService {

    private static final String DEFAULT_ISSUE_SCHEMA = "{\n"
        + "  \"type\": \"object\",\n"
        + "  \"properties\": {\n"
        + "    \"issues\": {\n"
        + "      \"type\": \"array\",\n"
        + "      \"items\": {\n"
        + "        \"type\": \"object\",\n"
        + "        \"properties\": {\n"
        + "          \"issueType\": { \"type\": \"string\" },\n"
        + "          \"severity\": { \"type\": \"string\" },\n"
        + "          \"filePath\": { \"type\": \"string\" },\n"
        + "          \"startLine\": { \"type\": \"integer\" },\n"
        + "          \"endLine\": { \"type\": \"integer\" },\n"
        + "          \"summary\": { \"type\": \"string\" },\n"
        + "          \"detail\": { \"type\": \"string\" },\n"
        + "          \"suggestion\": { \"type\": \"string\" },\n"
        + "          \"codeSnippet\": { \"type\": \"string\" }\n"
        + "        },\n"
        + "        \"required\": [\"issueType\", \"severity\", \"filePath\", \"summary\", \"suggestion\"]\n"
        + "      }\n"
        + "    }\n"
        + "  },\n"
        + "  \"required\": [\"issues\"]\n"
        + "}";

    private final DeepSeekProperties properties;

    private final SystemConfigAppService systemConfigAppService;

    private final ObjectMapper objectMapper;

    public AiDraftGenerationService(DeepSeekProperties properties,
                                    SystemConfigAppService systemConfigAppService,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.systemConfigAppService = systemConfigAppService;
        this.objectMapper = objectMapper;
    }

    public AiGeneratedScriptResponse generateScript(AiGenerateScriptRequest request) {
        try {
            AiGenerateScriptRequest safeRequest = request == null ? new AiGenerateScriptRequest() : request;
            String content = chat(scriptSystemPrompt(), scriptUserPrompt(safeRequest), 2500);
            AiGeneratedScriptResponse response = objectMapper.readValue(extractJson(content), AiGeneratedScriptResponse.class);
            fillScriptDefaults(response, safeRequest);
            normalizeScriptContent(response);
            validateScriptDraft(response);
            return response;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI 生成脚本规则草稿失败：" + exception.getMessage());
        }
    }

    public AiGeneratedSkillResponse generateSkill(AiGenerateSkillRequest request) {
        try {
            AiGenerateSkillRequest safeRequest = request == null ? new AiGenerateSkillRequest() : request;
            String content = chat(skillSystemPrompt(), skillUserPrompt(safeRequest), 2000);
            JsonNode root = objectMapper.readTree(extractJson(content));
            if (root.has("parametersSchema") && root.path("parametersSchema").isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) root).put(
                    "parametersSchema",
                    objectMapper.writeValueAsString(root.path("parametersSchema"))
                );
            }
            AiGeneratedSkillResponse response = objectMapper.treeToValue(root, AiGeneratedSkillResponse.class);
            fillSkillDefaults(response, safeRequest);
            normalizeSkillSchema(response);
            validateSkillDraft(response);
            return response;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI 生成 Skill 规则草稿失败：" + exception.getMessage());
        }
    }

    String scriptSystemPrompt() {
        return "你是代码检视平台的规则工程师，负责生成可直接保存到平台的脚本检视规则草稿。"
            + "必须只返回一个 JSON 对象，不要 Markdown，不要解释。"
            + "脚本语言仅允许 NODE，脚本必须使用 fs.readFileSync(0, 'utf8') 从 stdin 读取 JSON 输入，不能使用 /dev/stdin，不能访问网络、不能启动子进程、不能读取本地仓库文件。"
            + "平台输入 JSON 包含 projectName、projectType、branch、reviewDays、diffContent、filePaths。"
            + "脚本输出必须是 JSON 字符串，格式为 {\"issues\": []}。"
            + "issues 中每个问题字段必须包含 issueType、severity、filePath、startLine、endLine、summary、detail、suggestion、codeSnippet。"
            + "summary、detail、suggestion 必须使用中文描述，startLine 和 endLine 无法定位时可省略或置为 null。";
    }

    String skillSystemPrompt() {
        return "你是代码检视平台的 AI Skill 设计专家，负责生成 Function Calling 能使用的 Skill 草稿。"
            + "必须只返回一个 JSON 对象，不要 Markdown，不要解释。"
            + "生成的 parametersSchema 必须是合法 JSON Schema 对象或 JSON 字符串，根节点 type 必须为 object，必须包含 issues 数组。"
            + "issues 中每个问题字段必须包含 issueType、severity、filePath、startLine、endLine、summary、detail、suggestion、codeSnippet。"
            + "functionName 固定为 submit_review_issues。"
            + "promptTemplate 要能作为检视规则 Prompt 使用，必须包含 ${projectName}、${projectType}、${branch}、${reviewDays}、${diffContent} 占位符，并要求中文输出描述性字段。";
    }

    private String scriptUserPrompt(AiGenerateScriptRequest request) {
        return "请根据以下需求生成脚本规则草稿：\n"
            + "需求：" + value(request.getRequirement(), "生成一条 Java Web 项目常用检视规则") + "\n"
            + "项目类型：" + value(request.getProjectType(), "BACKEND") + "\n"
            + "问题类型：" + value(request.getRuleType(), "CUSTOM") + "\n"
            + "严重度：" + value(request.getSeverity(), "MAJOR") + "\n"
            + "脚本语言：" + value(request.getScriptLanguage(), "NODE") + "\n"
            + "返回 JSON 字段必须完整包含：scriptName、scriptCode、scriptLanguage、scriptContent、parameterTemplate、timeoutSeconds、"
            + "ruleName、ruleCode、ruleType、severity、projectType、promptTemplate。"
            + "scriptCode 和 ruleCode 使用大写下划线，timeoutSeconds 建议 10 到 30。";
    }

    private String skillUserPrompt(AiGenerateSkillRequest request) {
        return "请根据以下需求生成 AI Skill 草稿：\n"
            + "需求：" + value(request.getRequirement(), "生成一条 Java Web 项目常用 AI 检视 Skill") + "\n"
            + "项目类型：" + value(request.getProjectType(), "BACKEND") + "\n"
            + "问题类型：" + value(request.getRuleType(), "CUSTOM") + "\n"
            + "严重度：" + value(request.getSeverity(), "MAJOR") + "\n"
            + "返回 JSON 字段必须完整包含：skillName、skillCode、functionName、functionDescription、parametersSchema、version、"
            + "ruleName、ruleCode、ruleType、severity、projectType、promptTemplate。"
            + "skillCode 和 ruleCode 使用大写下划线，version 默认为 1.0.0。"
            + "parametersSchema 必须兼容平台 Function Calling，建议使用以下结构并按需求补充描述：\n" + DEFAULT_ISSUE_SCHEMA;
    }

    private String chat(String systemPrompt, String userPrompt, int maxTokens) throws Exception {
        String apiKey = systemConfigAppService.getDeepSeekApiKey(properties.getApiKey());
        String url = normalizeChatCompletionsUrl(systemConfigAppService.getDeepSeekUrl(properties.getUrl()));
        String model = systemConfigAppService.getDeepSeekModel(properties.getModel());
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek API Key 未配置");
        }
        if (!StringUtils.hasText(url)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek Base URL 未配置");
        }
        if (!StringUtils.hasText(model)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek 模型未配置");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        ResponseEntity<String> response = restTemplate().exchange(
            url,
            HttpMethod.POST,
            new HttpEntity<>(objectMapper.writeValueAsString(requestBody(model, systemPrompt, userPrompt, maxTokens)), headers),
            String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek 请求失败：HTTP " + response.getStatusCodeValue());
        }
        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText();
        if (!StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek 响应为空");
        }
        return content;
    }

    private Map<String, Object> requestBody(String model, String systemPrompt, String userPrompt, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.put("messages", messages);
        body.put("temperature", 0.2);
        body.put("max_tokens", maxTokens);
        return body;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                trimmed = trimmed.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI 未返回 JSON 对象");
        }
        return trimmed.substring(start, end + 1);
    }

    private void fillScriptDefaults(AiGeneratedScriptResponse response, AiGenerateScriptRequest request) {
        response.setScriptLanguage(value(response.getScriptLanguage(), "NODE"));
        response.setTimeoutSeconds(response.getTimeoutSeconds() == null ? 20 : response.getTimeoutSeconds());
        response.setProjectType(value(response.getProjectType(), value(request.getProjectType(), "BACKEND")));
        response.setRuleType(value(response.getRuleType(), value(request.getRuleType(), "CUSTOM")));
        response.setSeverity(value(response.getSeverity(), value(request.getSeverity(), "MAJOR")));
        response.setParameterTemplate(value(response.getParameterTemplate(), "stdin JSON：projectName、projectType、branch、reviewDays、diffContent、filePaths"));
    }

    private void normalizeScriptContent(AiGeneratedScriptResponse response) {
        String scriptContent = response.getScriptContent();
        if (!StringUtils.hasText(scriptContent)) {
            return;
        }
        scriptContent = scriptContent
            .replace("fs.readFileSync('/dev/stdin', 'utf8')", "fs.readFileSync(0, 'utf8')")
            .replace("fs.readFileSync(\"/dev/stdin\", \"utf8\")", "fs.readFileSync(0, 'utf8')")
            .replace("fs.readFileSync('/dev/stdin')", "fs.readFileSync(0, 'utf8')")
            .replace("fs.readFileSync(\"/dev/stdin\")", "fs.readFileSync(0, 'utf8')");
        if (scriptContent.contains("fs.readFileSync") && !scriptContent.contains("require('fs')") && !scriptContent.contains("require(\"fs\")")) {
            scriptContent = "const fs = require('fs');\n" + scriptContent;
        }
        response.setScriptContent(scriptContent);
    }

    private void fillSkillDefaults(AiGeneratedSkillResponse response, AiGenerateSkillRequest request) {
        response.setFunctionName(value(response.getFunctionName(), "submit_review_issues"));
        response.setVersion(value(response.getVersion(), "1.0.0"));
        response.setProjectType(value(response.getProjectType(), value(request.getProjectType(), "BACKEND")));
        response.setRuleType(value(response.getRuleType(), value(request.getRuleType(), "CUSTOM")));
        response.setSeverity(value(response.getSeverity(), value(request.getSeverity(), "MAJOR")));
        response.setParametersSchema(value(response.getParametersSchema(), DEFAULT_ISSUE_SCHEMA));
    }

    private void normalizeSkillSchema(AiGeneratedSkillResponse response) throws Exception {
        JsonNode schema = objectMapper.readTree(response.getParametersSchema());
        response.setParametersSchema(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema));
    }

    private void validateScriptDraft(AiGeneratedScriptResponse response) {
        require(response.getScriptName(), "scriptName");
        require(response.getScriptCode(), "scriptCode");
        require(response.getScriptContent(), "scriptContent");
        require(response.getRuleName(), "ruleName");
        require(response.getRuleCode(), "ruleCode");
        if (!"NODE".equalsIgnoreCase(response.getScriptLanguage())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "当前仅支持生成 NODE 脚本规则");
        }
    }

    private void validateSkillDraft(AiGeneratedSkillResponse response) throws Exception {
        require(response.getSkillName(), "skillName");
        require(response.getSkillCode(), "skillCode");
        require(response.getFunctionName(), "functionName");
        require(response.getFunctionDescription(), "functionDescription");
        require(response.getRuleName(), "ruleName");
        require(response.getRuleCode(), "ruleCode");
        JsonNode schema = objectMapper.readTree(response.getParametersSchema());
        if (!"object".equals(schema.path("type").asText()) || !schema.path("properties").has("issues")) {
            throw new BizException(ErrorCode.BIZ_ERROR, "parametersSchema 必须是包含 issues 的 object");
        }
    }

    private void require(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI 返回缺少字段：" + field);
        }
    }

    private String value(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
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

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = properties.getTimeoutSeconds() * 1000;
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }
}
