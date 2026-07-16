package com.cmbchina.codereview.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cmbchina.codereview.infrastructure.ocr.OpenCodeReviewProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenCodeReviewUsageCollectorTest {

    @TempDir
    Path tempDir;

    @Test
    void countsSuccessfulAndFailedCallsAndFallbackTokens() throws Exception {
        Path sessionDir = Files.createDirectories(tempDir.resolve("repository"));
        Files.write(sessionDir.resolve("session-1.jsonl"), Arrays.asList(
            "{\"type\":\"llm_request\"}",
            "{\"type\":\"llm_response\",\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20,"
                + "\"cache_read_tokens\":30,\"cache_write_tokens\":5}}",
            "{\"type\":\"llm_error\",\"error\":\"timeout\"}",
            "{\"type\":\"llm_response\",\"usage\":{\"prompt_tokens\":60,\"completion_tokens\":10}}"
        ), StandardCharsets.UTF_8);
        OpenCodeReviewProperties properties = new OpenCodeReviewProperties();
        properties.setSessionRoot(tempDir.toString());

        OpenCodeReviewUsageCollector.UsageStats stats = new OpenCodeReviewUsageCollector(
            properties, new ObjectMapper()).collect("session-1");

        assertEquals(3, stats.getCallCount());
        assertEquals(2, stats.getSuccessCount());
        assertEquals(1, stats.getFailureCount());
        assertEquals(160L, stats.getInputTokenCount());
        assertEquals(30L, stats.getOutputTokenCount());
        assertEquals(225L, stats.getTotalTokenCount());
        assertNull(stats.getWarning());
    }
}
