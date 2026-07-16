package com.cmbchina.codereview.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenCodeReviewResultParserTest {

    private final OpenCodeReviewResultParser parser = new OpenCodeReviewResultParser(new ObjectMapper());

    @Test
    void mapsOpenCodeReviewJsonToPlatformIssue() throws Exception {
        String payload = "{"
            + "\"status\":\"completed_with_warnings\","
            + "\"session_id\":\"session-1\"," 
            + "\"summary\":{\"files_reviewed\":3,\"input_tokens\":1200,\"output_tokens\":80,\"total_tokens\":1480,"
            + "\"cache_read_tokens\":150,\"cache_write_tokens\":50},"
            + "\"comments\":[{"
            + "\"path\":\"src/UserService.java\","
            + "\"content\":\"## 空指针风险\\n调用 user 前未校验非空\","
            + "\"suggestion_code\":\"if (user == null) return;\","
            + "\"existing_code\":\"user.getName();\","
            + "\"start_line\":12,\"end_line\":12,"
            + "\"category\":\"bug\",\"severity\":\"high\"}],"
            + "\"warnings\":[{\"file\":\"src/Other.java\",\"message\":\"review timeout\"}]"
            + "}";

        OpenCodeReviewResultParser.ParsedResult result = parser.parse(payload, 11L, project());

        assertEquals("completed_with_warnings", result.getStatus());
        assertEquals("session-1", result.getSessionId());
        assertEquals(1200L, result.getInputTokenCount());
        assertEquals(80L, result.getOutputTokenCount());
        assertEquals(1480L, result.getTotalTokenCount());
        assertEquals(150L, result.getCacheReadTokenCount());
        assertEquals(50L, result.getCacheWriteTokenCount());
        assertEquals(3, result.getFilesReviewed());
        assertEquals(1, result.getWarnings().size());
        assertEquals(1, result.getIssues().size());
        ReviewIssueEntity issue = result.getIssues().get(0);
        assertEquals("OCR", issue.getIssueSource());
        assertEquals("HIGH", issue.getSeverity());
        assertEquals("BUG", issue.getIssueType());
        assertEquals("空指针风险", issue.getSummary());
        assertEquals("src/UserService.java", issue.getFilePath());
        assertEquals(12, issue.getStartLine());
        assertEquals("user.getName();", issue.getCodeSnippet());
        assertEquals("if (user == null) return;", issue.getSuggestion());
    }

    private Project project() {
        Project project = new Project();
        project.setId(22L);
        project.setProjectName("demo");
        return project;
    }
}
