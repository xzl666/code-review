package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.util.MaskUtils;
import com.cmbchina.codereview.domain.config.SystemConfig;
import com.cmbchina.codereview.domain.config.SystemConfigRepository;
import com.cmbchina.codereview.interfaces.dto.request.DefaultTokenUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.request.DeepSeekConfigUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.ConfigValidationResponse;
import com.cmbchina.codereview.interfaces.dto.response.DefaultTokenResponse;
import com.cmbchina.codereview.interfaces.dto.response.DeepSeekConfigResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

@Service
public class SystemConfigAppService {

    public static final String DEFAULT_GITEE_TOKEN_KEY = "DEFAULT_GITEE_TOKEN";

    public static final String DEEPSEEK_API_KEY = "DEEPSEEK_API_KEY";

    public static final String DEEPSEEK_URL = "DEEPSEEK_URL";

    public static final String DEEPSEEK_MODEL = "DEEPSEEK_MODEL";

    private final SystemConfigRepository systemConfigRepository;

    private final ObjectMapper objectMapper;

    @Value("${code-review.git.gitee-token:${CODE_REVIEW_GITEE_TOKEN:}}")
    private String defaultGiteeTokenFallback;

    public SystemConfigAppService(SystemConfigRepository systemConfigRepository,
                                  ObjectMapper objectMapper) {
        this.systemConfigRepository = systemConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDefaultGiteeToken(DefaultTokenUpdateRequest request) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(DEFAULT_GITEE_TOKEN_KEY);
        config.setConfigValue(request.getToken());
        config.setConfigDesc("系统默认 Gitee 访问令牌，项目未配置单独令牌时使用");
        systemConfigRepository.saveOrUpdate(config);
    }

    public String getDefaultGiteeToken() {
        SystemConfig config = systemConfigRepository.findByKey(DEFAULT_GITEE_TOKEN_KEY);
        return defaultIfBlank(defaultGiteeTokenFallback, config == null ? null : config.getConfigValue());
    }

    public DefaultTokenResponse getDefaultGiteeTokenDetail() {
        String token = getDefaultGiteeToken();
        DefaultTokenResponse response = new DefaultTokenResponse();
        response.setConfigured(StringUtils.hasText(token));
        response.setMaskedToken(StringUtils.hasText(token) ? MaskUtils.maskSecret(token) : null);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDeepSeekConfig(DeepSeekConfigUpdateRequest request) {
        saveConfig(DEEPSEEK_API_KEY, request.getApiKey(), "DeepSeek API Key");
        if (StringUtils.hasText(request.getUrl())) {
            saveConfig(DEEPSEEK_URL, request.getUrl(), "DeepSeek OpenAI compatible chat completions URL");
        }
        if (StringUtils.hasText(request.getModel())) {
            saveConfig(DEEPSEEK_MODEL, request.getModel(), "DeepSeek model name");
        }
    }

    public DeepSeekConfigResponse getDeepSeekConfigDetail(String fallbackApiKey, String fallbackUrl, String fallbackModel) {
        String apiKey = fallbackApiKey;
        String url = fallbackUrl;
        String model = fallbackModel;
        if (!StringUtils.hasText(apiKey)) {
            apiKey = getConfigValue(DEEPSEEK_API_KEY);
        }
        if (!StringUtils.hasText(url)) {
            url = getConfigValue(DEEPSEEK_URL);
        }
        if (!StringUtils.hasText(model)) {
            model = getConfigValue(DEEPSEEK_MODEL);
        }
        DeepSeekConfigResponse response = new DeepSeekConfigResponse();
        response.setConfigured(StringUtils.hasText(apiKey));
        response.setMaskedApiKey(StringUtils.hasText(apiKey) ? MaskUtils.maskSecret(apiKey) : null);
        response.setUrl(url);
        response.setModel(model);
        return response;
    }

    public String getDeepSeekApiKey(String fallbackApiKey) {
        return defaultIfBlank(fallbackApiKey, getConfigValue(DEEPSEEK_API_KEY));
    }

    public String getDeepSeekUrl(String fallbackUrl) {
        return defaultIfBlank(fallbackUrl, getConfigValue(DEEPSEEK_URL));
    }

    public String getDeepSeekModel(String fallbackModel) {
        return defaultIfBlank(fallbackModel, getConfigValue(DEEPSEEK_MODEL));
    }

    public ConfigValidationResponse validateDefaultGiteeToken() {
        String token = getDefaultGiteeToken();
        if (!StringUtils.hasText(token)) {
            return failed("Gitee Token 未配置");
        }
        try {
            String url = "https://gitee.com/api/v5/user?access_token="
                + UriUtils.encodeQueryParam(token, StandardCharsets.UTF_8);
            ResponseEntity<String> response = restTemplate().exchange(
                url,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
            );
            return result(
                response.getStatusCode().is2xxSuccessful(),
                response.getStatusCodeValue(),
                response.getStatusCode().is2xxSuccessful() ? "Gitee Token 验证通过" : "Gitee Token 验证未通过",
                truncate(response.getBody())
            );
        } catch (Exception exception) {
            return failed("Gitee Token 验证失败：" + exception.getMessage());
        }
    }

    public ConfigValidationResponse validateDeepSeekConfig(String fallbackApiKey, String fallbackUrl, String fallbackModel) {
        String apiKey = getDeepSeekApiKey(fallbackApiKey);
        String url = normalizeChatCompletionsUrl(getDeepSeekUrl(fallbackUrl));
        String model = getDeepSeekModel(fallbackModel);
        if (!StringUtils.hasText(apiKey)) {
            return failed("DeepSeek API Key 未配置");
        }
        if (!StringUtils.hasText(url)) {
            return failed("DeepSeek Base URL 未配置");
        }
        if (!StringUtils.hasText(model)) {
            return failed("DeepSeek 模型名称未配置");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            ResponseEntity<String> response = restTemplate().exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(deepSeekValidationBody(model)), headers),
                String.class
            );
            return result(
                response.getStatusCode().is2xxSuccessful(),
                response.getStatusCodeValue(),
                response.getStatusCode().is2xxSuccessful() ? "DeepSeek 配置验证通过" : "DeepSeek 配置验证未通过",
                truncate(response.getBody())
            );
        } catch (Exception exception) {
            return failed("DeepSeek 配置验证失败：" + exception.getMessage());
        }
    }

    private String getConfigValue(String key) {
        SystemConfig config = systemConfigRepository.findByKey(key);
        return config == null ? null : config.getConfigValue();
    }

    private void saveConfig(String key, String value, String desc) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setConfigDesc(desc);
        systemConfigRepository.saveOrUpdate(config);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Map<String, Object> deepSeekValidationBody(String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", "ping");
        messages.add(message);
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("max_tokens", 1);
        return body;
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

    private ConfigValidationResponse failed(String message) {
        return result(false, null, message, "");
    }

    private ConfigValidationResponse result(Boolean success, Integer statusCode, String message, String responseBody) {
        ConfigValidationResponse response = new ConfigValidationResponse();
        response.setSuccess(success);
        response.setStatusCode(statusCode);
        response.setMessage(message);
        response.setResponseBody(responseBody);
        return response;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
