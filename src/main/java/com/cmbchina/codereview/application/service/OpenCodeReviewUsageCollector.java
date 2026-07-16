package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.infrastructure.ocr.OpenCodeReviewProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenCodeReviewUsageCollector {

    private final OpenCodeReviewProperties properties;
    private final ObjectMapper objectMapper;

    public OpenCodeReviewUsageCollector(OpenCodeReviewProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public UsageStats collect(String sessionId) {
        UsageStats stats = new UsageStats();
        if (!StringUtils.hasText(sessionId)) {
            return stats;
        }
        Path root = sessionRoot();
        if (!Files.isDirectory(root)) {
            stats.setWarning("未找到 OpenCodeReview 会话目录，模型调用成功/失败次数无法统计");
            return stats;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            Path sessionFile = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(sessionId + ".jsonl"))
                .findFirst()
                .orElse(null);
            if (sessionFile == null) {
                stats.setWarning("未找到 OpenCodeReview 会话记录，模型调用成功/失败次数无法统计");
                return stats;
            }
            readSession(sessionFile, stats);
        } catch (Exception exception) {
            stats.setWarning("读取 OpenCodeReview 会话用量失败：" + exception.getMessage());
        }
        stats.setCallCount(stats.getSuccessCount() + stats.getFailureCount());
        return stats;
    }

    private void readSession(Path sessionFile, UsageStats stats) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(sessionFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                JsonNode record = objectMapper.readTree(line);
                String type = record.path("type").asText("");
                if ("llm_response".equals(type)) {
                    stats.setSuccessCount(stats.getSuccessCount() + 1);
                    JsonNode usage = record.path("usage");
                    stats.setInputTokenCount(stats.getInputTokenCount() + usage.path("prompt_tokens").asLong(0L));
                    stats.setOutputTokenCount(stats.getOutputTokenCount() + usage.path("completion_tokens").asLong(0L));
                    stats.setCacheReadTokenCount(stats.getCacheReadTokenCount() + usage.path("cache_read_tokens").asLong(0L));
                    stats.setCacheWriteTokenCount(stats.getCacheWriteTokenCount() + usage.path("cache_write_tokens").asLong(0L));
                } else if ("llm_error".equals(type)) {
                    stats.setFailureCount(stats.getFailureCount() + 1);
                }
            }
        }
        stats.setTotalTokenCount(stats.getInputTokenCount() + stats.getOutputTokenCount()
            + stats.getCacheReadTokenCount() + stats.getCacheWriteTokenCount());
    }

    private Path sessionRoot() {
        if (StringUtils.hasText(properties.getSessionRoot())) {
            return Paths.get(properties.getSessionRoot());
        }
        return Paths.get(System.getProperty("user.home"), ".opencodereview", "sessions");
    }

    @Data
    public static class UsageStats {
        private Integer callCount = 0;
        private Integer successCount = 0;
        private Integer failureCount = 0;
        private Long inputTokenCount = 0L;
        private Long outputTokenCount = 0L;
        private Long totalTokenCount = 0L;
        private Long cacheReadTokenCount = 0L;
        private Long cacheWriteTokenCount = 0L;
        private String warning;
    }
}
