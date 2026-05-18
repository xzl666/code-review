package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.util.MaskUtils;
import com.cmbchina.codereview.domain.config.SystemConfig;
import com.cmbchina.codereview.domain.config.SystemConfigRepository;
import com.cmbchina.codereview.interfaces.dto.request.DefaultTokenUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.request.DeepSeekConfigUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.DefaultTokenResponse;
import com.cmbchina.codereview.interfaces.dto.response.DeepSeekConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemConfigAppService {

    public static final String DEFAULT_GITEE_TOKEN_KEY = "DEFAULT_GITEE_TOKEN";

    public static final String DEEPSEEK_API_KEY = "DEEPSEEK_API_KEY";

    public static final String DEEPSEEK_URL = "DEEPSEEK_URL";

    public static final String DEEPSEEK_MODEL = "DEEPSEEK_MODEL";

    private final SystemConfigRepository systemConfigRepository;

    @Value("${code-review.git.gitee-token:${CODE_REVIEW_GITEE_TOKEN:}}")
    private String defaultGiteeTokenFallback;

    public SystemConfigAppService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
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
}
