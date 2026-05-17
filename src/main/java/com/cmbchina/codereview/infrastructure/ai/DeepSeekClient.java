package com.cmbchina.codereview.infrastructure.ai;

import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.application.service.SystemConfigAppService;
import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.AiSkillEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.domain.project.Project;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

@Component
public class DeepSeekClient {

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
        String url = systemConfigAppService.getDeepSeekUrl(properties.getUrl());
        String model = systemConfigAppService.getDeepSeekModel(properties.getModel());
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek API key is not configured");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(requestBody(project, rule, skill, chunk, branch, reviewDays, model));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            ResponseEntity<String> response = restTemplate().exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek request failed: HTTP " + response.getStatusCodeValue() + "; " + response.getBody());
            }
            return extractArguments(response.getBody());
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek request error: " + exception.getMessage());
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
        body.put("messages", messages(project, rule, chunk, branch, reviewDays));
        body.put("tools", tools(skill));
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", skill.getFunctionName());
        Map<String, Object> toolChoice = new LinkedHashMap<>();
        toolChoice.put("type", "function");
        toolChoice.put("function", function);
        body.put("tool_choice", toolChoice);
        body.put("temperature", 0.1);
        return body;
    }

    private List<Map<String, String>> messages(Project project,
                                               ReviewRuleEntity rule,
                                               DiffChunk chunk,
                                               String branch,
                                               Integer reviewDays) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", "You are a strict code review assistant. Return only structured issues through the provided function."));
        String prompt = renderPrompt(rule.getPromptTemplate(), project, chunk, branch, reviewDays);
        messages.add(message("user", prompt));
        return messages;
    }

    private String renderPrompt(String template, Project project, DiffChunk chunk, String branch, Integer reviewDays) {
        String prompt = StringUtils.hasText(template) ? template : "Review the following git diff and report code issues.";
        prompt = prompt.replace("${projectName}", value(project.getProjectName()));
        prompt = prompt.replace("${projectType}", value(project.getProjectType()));
        prompt = prompt.replace("${branch}", value(branch));
        prompt = prompt.replace("${reviewDays}", String.valueOf(reviewDays));
        prompt = prompt.replace("${diffContent}", value(chunk.getContent()));
        return prompt + "\n\nFile: " + chunk.getFilePath() + "\nDiff:\n" + chunk.getContent();
    }

    private List<Map<String, Object>> tools(AiSkillEntity skill) throws Exception {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", skill.getFunctionName());
        function.put("description", skill.getFunctionDescription());
        function.put("parameters", objectMapper.readTree(skill.getParametersSchema()));
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool);
        return tools;
    }

    private String extractArguments(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode message = root.path("choices").path(0).path("message");
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray() && toolCalls.size() > 0) {
            JsonNode arguments = toolCalls.path(0).path("function").path("arguments");
            if (!arguments.isMissingNode() && !arguments.isNull()) {
                return arguments.asText();
            }
        }
        JsonNode content = message.path("content");
        if (!content.isMissingNode() && !content.isNull()) {
            return content.asText();
        }
        throw new BizException(ErrorCode.BIZ_ERROR, "DeepSeek response has no function arguments");
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

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = properties.getTimeoutSeconds() * 1000;
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return new RestTemplate(factory);
    }
}
