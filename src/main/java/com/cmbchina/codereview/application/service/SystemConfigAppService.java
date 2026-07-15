package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.util.MaskUtils;
import com.cmbchina.codereview.domain.config.SystemConfig;
import com.cmbchina.codereview.domain.config.SystemConfigRepository;
import com.cmbchina.codereview.interfaces.dto.request.DefaultTokenUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.request.DeepSeekConfigUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.request.ModelConfigSaveRequest;
import com.cmbchina.codereview.interfaces.dto.request.ModelConfigValidateRequest;
import com.cmbchina.codereview.interfaces.dto.response.ConfigValidationResponse;
import com.cmbchina.codereview.interfaces.dto.response.DefaultTokenResponse;
import com.cmbchina.codereview.interfaces.dto.response.DeepSeekConfigResponse;
import com.cmbchina.codereview.interfaces.dto.response.ModelConfigResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    @Value("${code-review.git.gitee-token:${CODE_REVIEW_GITEE_TOKEN:}}")
    private String defaultGiteeTokenFallback;

    public SystemConfigAppService(SystemConfigRepository systemConfigRepository,
                                  ObjectMapper objectMapper,
                                  JdbcTemplate jdbcTemplate) {
        this.systemConfigRepository = systemConfigRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
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

    public ConfigValidationResponse validateDefaultGiteeToken() {
        String token = getDefaultGiteeToken();
        if (!StringUtils.hasText(token)) {
            return failed("Gitee Token 未配置");
        }
        try {
            String url = "https://gitee.com/api/v5/user?access_token="
                + UriUtils.encodeQueryParam(token, StandardCharsets.UTF_8);
            ResponseEntity<String> response = restTemplate(10).exchange(
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

    @Transactional(rollbackFor = Exception.class)
    public void updateDeepSeekConfig(DeepSeekConfigUpdateRequest request) {
        ModelConfigSaveRequest saveRequest = new ModelConfigSaveRequest();
        ActiveModelConfig active = getActiveModelConfig(null, null, null);
        saveRequest.setConfigName("DeepSeek 默认配置");
        saveRequest.setProviderType("OPENAI_COMPATIBLE");
        saveRequest.setBaseUrl(defaultIfBlank(request.getUrl(), active.getBaseUrl()));
        saveRequest.setModelName(defaultIfBlank(request.getModel(), active.getModelName()));
        saveRequest.setApiKey(defaultIfBlank(request.getApiKey(), active.getApiKey()));
        saveRequest.setEnabled(1);
        saveModelConfig(saveRequest);
    }

    public DeepSeekConfigResponse getDeepSeekConfigDetail(String fallbackApiKey, String fallbackUrl, String fallbackModel) {
        ActiveModelConfig active = getActiveModelConfig(fallbackApiKey, fallbackUrl, fallbackModel);
        DeepSeekConfigResponse response = new DeepSeekConfigResponse();
        response.setConfigured(StringUtils.hasText(active.getApiKey()));
        response.setMaskedApiKey(StringUtils.hasText(active.getApiKey()) ? MaskUtils.maskSecret(active.getApiKey()) : null);
        response.setUrl(active.getBaseUrl());
        response.setModel(active.getModelName());
        return response;
    }

    public String getDeepSeekApiKey(String fallbackApiKey) {
        return getActiveModelConfig(fallbackApiKey, null, null).getApiKey();
    }

    public String getDeepSeekUrl(String fallbackUrl) {
        return getActiveModelConfig(null, fallbackUrl, null).getBaseUrl();
    }

    public String getDeepSeekModel(String fallbackModel) {
        return getActiveModelConfig(null, null, fallbackModel).getModelName();
    }

    public ActiveModelConfig getActiveModelConfig(String fallbackApiKey, String fallbackUrl, String fallbackModel) {
        ModelConfigResponse active = activeModelConfig();
        String apiKey = active == null ? null : apiKeyById(active.getId());
        String baseUrl = active == null ? null : active.getBaseUrl();
        String modelName = active == null ? null : active.getModelName();
        if (!StringUtils.hasText(apiKey)) {
            apiKey = defaultIfBlank(fallbackApiKey, getConfigValue(DEEPSEEK_API_KEY));
        }
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = defaultIfBlank(fallbackUrl, getConfigValue(DEEPSEEK_URL));
        }
        if (!StringUtils.hasText(modelName)) {
            modelName = defaultIfBlank(fallbackModel, getConfigValue(DEEPSEEK_MODEL));
        }
        return new ActiveModelConfig(apiKey, baseUrl, modelName);
    }

    public List<ModelConfigResponse> listModelConfigs() {
        return jdbcTemplate.query(
            "SELECT id, config_name, provider_type, base_url, model_name, api_key, enabled, remark, create_time, update_time "
                + "FROM cr_model_config WHERE deleted = 0 ORDER BY enabled DESC, update_time DESC, id DESC",
            (rs, rowNum) -> {
                ModelConfigResponse response = new ModelConfigResponse();
                response.setId(rs.getLong("id"));
                response.setConfigName(rs.getString("config_name"));
                response.setProviderType(rs.getString("provider_type"));
                response.setBaseUrl(rs.getString("base_url"));
                response.setModelName(rs.getString("model_name"));
                String apiKey = rs.getString("api_key");
                response.setConfigured(StringUtils.hasText(apiKey));
                response.setMaskedApiKey(StringUtils.hasText(apiKey) ? MaskUtils.maskSecret(apiKey) : null);
                response.setEnabled(rs.getInt("enabled"));
                response.setRemark(rs.getString("remark"));
                response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
                response.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
                return response;
            }
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveModelConfig(ModelConfigSaveRequest request) {
        String providerType = defaultIfBlank(request.getProviderType(), "OPENAI_COMPATIBLE");
        int enabled = request.getEnabled() == null ? 0 : request.getEnabled();
        if (request.getId() == null) {
            jdbcTemplate.update(
                "INSERT INTO cr_model_config (config_name, provider_type, base_url, model_name, api_key, enabled, remark) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                request.getConfigName(),
                providerType,
                request.getBaseUrl(),
                request.getModelName(),
                request.getApiKey(),
                enabled,
                request.getRemark()
            );
            if (enabled == 1) {
                enableModelConfig(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
            }
            return;
        }
        ensureModelConfig(request.getId());
        String apiKey = StringUtils.hasText(request.getApiKey()) ? request.getApiKey() : apiKeyById(request.getId());
        jdbcTemplate.update(
            "UPDATE cr_model_config SET config_name = ?, provider_type = ?, base_url = ?, model_name = ?, api_key = ?, enabled = ?, remark = ? "
                + "WHERE id = ? AND deleted = 0",
            request.getConfigName(),
            providerType,
            request.getBaseUrl(),
            request.getModelName(),
            apiKey,
            enabled,
            request.getRemark(),
            request.getId()
        );
        if (enabled == 1) {
            enableModelConfig(request.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void enableModelConfig(Long id) {
        ensureModelConfig(id);
        jdbcTemplate.update("UPDATE cr_model_config SET enabled = 0 WHERE deleted = 0");
        jdbcTemplate.update("UPDATE cr_model_config SET enabled = 1 WHERE id = ? AND deleted = 0", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteModelConfig(Long id) {
        ModelConfigResponse config = ensureModelConfig(id);
        if (config.getEnabled() != null && config.getEnabled() == 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "启用中的模型配置不能删除");
        }
        jdbcTemplate.update("UPDATE cr_model_config SET deleted = 1, enabled = 0 WHERE id = ?", id);
    }

    public ConfigValidationResponse validateModelConfig(ModelConfigValidateRequest request) {
        ModelConfigValidateRequest safeRequest = request == null ? new ModelConfigValidateRequest() : request;
        String apiKey = safeRequest.getApiKey();
        String baseUrl = safeRequest.getBaseUrl();
        String modelName = safeRequest.getModelName();
        if (safeRequest.getId() != null) {
            ModelConfigResponse existing = ensureModelConfig(safeRequest.getId());
            apiKey = defaultIfBlank(apiKey, apiKeyById(existing.getId()));
            baseUrl = defaultIfBlank(baseUrl, existing.getBaseUrl());
            modelName = defaultIfBlank(modelName, existing.getModelName());
        } else {
            ActiveModelConfig active = getActiveModelConfig(null, null, null);
            apiKey = defaultIfBlank(apiKey, active.getApiKey());
            baseUrl = defaultIfBlank(baseUrl, active.getBaseUrl());
            modelName = defaultIfBlank(modelName, active.getModelName());
        }
        return validateOpenAiCompatibleConfig(apiKey, baseUrl, modelName);
    }

    public ConfigValidationResponse validateDeepSeekConfig(String fallbackApiKey, String fallbackUrl, String fallbackModel) {
        ActiveModelConfig active = getActiveModelConfig(fallbackApiKey, fallbackUrl, fallbackModel);
        return validateOpenAiCompatibleConfig(active.getApiKey(), active.getBaseUrl(), active.getModelName());
    }

    private ConfigValidationResponse validateOpenAiCompatibleConfig(String apiKey, String baseUrl, String model) {
        String url = normalizeChatCompletionsUrl(baseUrl);
        if (!StringUtils.hasText(apiKey)) {
            return failed("API Key 未配置");
        }
        if (!StringUtils.hasText(url)) {
            return failed("Base URL 未配置");
        }
        if (!StringUtils.hasText(model)) {
            return failed("模型名称未配置");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            ResponseEntity<String> response = restTemplate(10).exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(modelValidationBody(model)), headers),
                String.class
            );
            return result(
                response.getStatusCode().is2xxSuccessful(),
                response.getStatusCodeValue(),
                response.getStatusCode().is2xxSuccessful() ? "模型配置验证通过" : "模型配置验证未通过",
                truncate(response.getBody())
            );
        } catch (Exception exception) {
            return failed("模型配置验证失败：" + exception.getMessage());
        }
    }

    private ModelConfigResponse activeModelConfig() {
        List<ModelConfigResponse> configs = listModelConfigs();
        return configs.stream()
            .filter(config -> config.getEnabled() != null && config.getEnabled() == 1)
            .findFirst()
            .orElse(configs.isEmpty() ? null : configs.get(0));
    }

    private ModelConfigResponse ensureModelConfig(Long id) {
        return listModelConfigs().stream()
            .filter(config -> config.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "模型配置不存在"));
    }

    private String apiKeyById(Long id) {
        if (id == null) {
            return null;
        }
        return jdbcTemplate.query(
            "SELECT api_key FROM cr_model_config WHERE id = ? AND deleted = 0",
            rs -> rs.next() ? rs.getString("api_key") : null,
            id
        );
    }

    private String getConfigValue(String key) {
        SystemConfig config = systemConfigRepository.findByKey(key);
        return config == null ? null : config.getConfigValue();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Map<String, Object> modelValidationBody(String model) {
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

    private RestTemplate restTemplate(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = timeoutSeconds * 1000;
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return new RestTemplate(factory);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
