package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.enums.IssueSource;
import com.cmbchina.codereview.common.enums.ReviewIssueStatus;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenCodeReviewResultParser {

    private final ObjectMapper objectMapper;

    public OpenCodeReviewResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedResult parse(String payload, Long taskId, Project project) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("OpenCodeReview 未返回 JSON 对象");
        }
        JsonNode comments = root.path("comments");
        if (!comments.isArray()) {
            throw new IllegalArgumentException("OpenCodeReview JSON 缺少 comments 数组");
        }

        ParsedResult result = new ParsedResult();
        result.status = root.path("status").asText("");
        result.sessionId = root.path("session_id").asText("");
        JsonNode summary = root.path("summary");
        result.filesReviewed = summary.path("files_reviewed").asInt(0);
        result.inputTokenCount = summary.path("input_tokens").asLong(0L);
        result.outputTokenCount = summary.path("output_tokens").asLong(0L);
        result.totalTokenCount = summary.path("total_tokens").asLong(0L);
        result.cacheReadTokenCount = summary.path("cache_read_tokens").asLong(0L);
        result.cacheWriteTokenCount = summary.path("cache_write_tokens").asLong(0L);
        JsonNode warnings = root.path("warnings");
        if (warnings.isArray()) {
            for (JsonNode warning : warnings) {
                String message = warning.path("message").asText("");
                String file = warning.path("file").asText("");
                if (StringUtils.hasText(message)) {
                    result.warnings.add(StringUtils.hasText(file) ? file + ": " + message : message);
                }
            }
        }
        for (JsonNode comment : comments) {
            result.issues.add(toIssue(comment, payload, taskId, project));
        }
        return result;
    }

    private ReviewIssueEntity toIssue(JsonNode comment, String payload, Long taskId, Project project) {
        String content = comment.path("content").asText("").trim();
        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setTaskId(taskId);
        issue.setProjectId(project.getId());
        issue.setIssueSource(IssueSource.OCR.name());
        issue.setSeverity(severity(comment.path("severity").asText("")));
        issue.setIssueType(issueType(comment.path("category").asText("")));
        issue.setFilePath(comment.path("path").asText(""));
        issue.setStartLine(line(comment.path("start_line").asInt(0)));
        issue.setEndLine(line(comment.path("end_line").asInt(0)));
        issue.setSummary(summary(content));
        issue.setDetail(content);
        issue.setSuggestion(comment.path("suggestion_code").asText(""));
        issue.setCodeSnippet(comment.path("existing_code").asText(""));
        issue.setRawResponse(limit(payload, 10000));
        issue.setStatus(ReviewIssueStatus.OPEN.name());
        return issue;
    }

    private String summary(String content) {
        if (!StringUtils.hasText(content)) {
            return "OpenCodeReview 检视意见";
        }
        String firstLine = content.split("\\R", 2)[0]
            .replaceFirst("^#{1,6}\\s*", "")
            .replaceFirst("^[-*]\\s*", "")
            .trim();
        return limit(firstLine, 255);
    }

    private String severity(String value) {
        switch (value == null ? "" : value.toLowerCase()) {
            case "critical":
                return "CRITICAL";
            case "high":
                return "HIGH";
            case "medium":
                return "MEDIUM";
            case "low":
            default:
                return "LOW";
        }
    }

    private String issueType(String value) {
        return StringUtils.hasText(value) ? value.toUpperCase() : "OTHER";
    }

    private Integer line(int value) {
        return value > 0 ? value : null;
    }

    private String limit(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public static class ParsedResult {
        private final List<ReviewIssueEntity> issues = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private String status;
        private String sessionId;
        private Long inputTokenCount = 0L;
        private Long outputTokenCount = 0L;
        private Long totalTokenCount = 0L;
        private Long cacheReadTokenCount = 0L;
        private Long cacheWriteTokenCount = 0L;
        private Integer filesReviewed = 0;

        public List<ReviewIssueEntity> getIssues() {
            return issues;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public String getStatus() {
            return status;
        }

        public String getSessionId() {
            return sessionId;
        }

        public Long getInputTokenCount() {
            return inputTokenCount;
        }

        public Long getOutputTokenCount() {
            return outputTokenCount;
        }

        public Long getTotalTokenCount() {
            return totalTokenCount;
        }

        public Long getCacheReadTokenCount() {
            return cacheReadTokenCount;
        }

        public Long getCacheWriteTokenCount() {
            return cacheWriteTokenCount;
        }

        public Integer getFilesReviewed() {
            return filesReviewed;
        }
    }
}
