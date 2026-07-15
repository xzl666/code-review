package com.cmbchina.codereview.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekProperties {

    @Value("${code-review.ai.deepseek.api-key:${CODE_REVIEW_DEEPSEEK_API_KEY:}}")
    private String apiKey;

    @Value("${code-review.ai.deepseek.url:${CODE_REVIEW_DEEPSEEK_URL:${CODE_REVIEW_DEEPSEEK_BASE_URL:https://zhenze-huhehaote.cmecloud.cn}}}")
    private String url;

    @Value("${code-review.ai.deepseek.model:${CODE_REVIEW_DEEPSEEK_MODEL:deepseek-v4-flash}}")
    private String model;

    @Value("${code-review.ai.deepseek.timeout-seconds:${CODE_REVIEW_DEEPSEEK_TIMEOUT_SECONDS:60}}")
    private Integer timeoutSeconds;

    @Value("${code-review.ai.deepseek.max-tokens:${CODE_REVIEW_DEEPSEEK_MAX_TOKENS:2048}}")
    private Integer maxTokens;

    @Value("${code-review.ai.max-diff-chars-per-request:${CODE_REVIEW_AI_MAX_DIFF_CHARS:12000}}")
    private Integer maxDiffCharsPerRequest;

    @Value("${code-review.ai.max-chunks-per-task:${CODE_REVIEW_AI_MAX_CHUNKS_PER_TASK:3}}")
    private Integer maxChunksPerTask;

    @Value("${code-review.ai.debug-log-enabled:${CODE_REVIEW_AI_DEBUG_LOG_ENABLED:true}}")
    private Boolean debugLogEnabled;

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

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Integer getMaxDiffCharsPerRequest() {
        return maxDiffCharsPerRequest;
    }

    public Integer getMaxChunksPerTask() {
        return maxChunksPerTask;
    }

    public Boolean getDebugLogEnabled() {
        return debugLogEnabled;
    }
}
