package com.cmbchina.codereview.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekProperties {

    @Value("${code-review.ai.deepseek.api-key:${CODE_REVIEW_DEEPSEEK_API_KEY:}}")
    private String apiKey;

    @Value("${code-review.ai.deepseek.url:${CODE_REVIEW_DEEPSEEK_URL:https://api.deepseek.com/chat/completions}}")
    private String url;

    @Value("${code-review.ai.deepseek.model:${CODE_REVIEW_DEEPSEEK_MODEL:deepseek-chat}}")
    private String model;

    @Value("${code-review.ai.deepseek.timeout-seconds:${CODE_REVIEW_DEEPSEEK_TIMEOUT_SECONDS:60}}")
    private Integer timeoutSeconds;

    @Value("${code-review.ai.max-diff-chars-per-request:${CODE_REVIEW_AI_MAX_DIFF_CHARS:30000}}")
    private Integer maxDiffCharsPerRequest;

    public String getApiKey() {
        return apiKey;
    }

    public String getUrl() {
        return url;
    }

    public String getModel() {
        return model;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Integer getMaxDiffCharsPerRequest() {
        return maxDiffCharsPerRequest;
    }
}
