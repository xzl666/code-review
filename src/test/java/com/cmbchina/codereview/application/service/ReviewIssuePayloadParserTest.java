package com.cmbchina.codereview.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cmbchina.codereview.common.enums.IssueSource;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewIssuePayloadParserTest {

    private final ReviewIssuePayloadParser parser = new ReviewIssuePayloadParser(new ObjectMapper());

    @Test
    void parsesAiIssueAliasesAndNormalizesValues() throws Exception {
        String payload = "{"
            + "\"issues\":[{"
            + "\"filename\":\"src/App.java\","
            + "\"line\":42,"
            + "\"lineEnd\":45,"
            + "\"message\":\"空指针风险\","
            + "\"description\":\"调用前未检查对象是否为空\","
            + "\"suggestedFix\":\"请在调用前增加非空判断\","
            + "\"snippet\":\"user.getName()\","
            + "\"severity\":\"high\""
            + "}]}";

        List<ReviewIssueEntity> issues = parser.parse(
            payload,
            11L,
            project(),
            rule(),
            33L,
            IssueSource.AI,
            "fallback.java"
        );

        assertEquals(1, issues.size());
        ReviewIssueEntity issue = issues.get(0);
        assertEquals(11L, issue.getTaskId());
        assertEquals(22L, issue.getProjectId());
        assertEquals(55L, issue.getRuleId());
        assertEquals(33L, issue.getSkillId());
        assertEquals("AI", issue.getIssueSource());
        assertEquals("MAJOR", issue.getSeverity());
        assertEquals("src/App.java", issue.getFilePath());
        assertEquals(42, issue.getStartLine());
        assertEquals(45, issue.getEndLine());
        assertEquals("空指针风险", issue.getSummary());
        assertEquals("调用前未检查对象是否为空", issue.getDetail());
        assertEquals("请在调用前增加非空判断", issue.getSuggestion());
        assertEquals("user.getName()", issue.getCodeSnippet());
        assertEquals("OPEN", issue.getStatus());
    }

    @Test
    void parsesArrayPayloadAndUsesFallbacks() throws Exception {
        String payload = "[{\"newLine\":0,\"recommendation\":\"建议补充测试\",\"severity\":\"notice\"}]";

        List<ReviewIssueEntity> issues = parser.parse(
            payload,
            11L,
            project(),
            rule(),
            null,
            IssueSource.SCRIPT,
            "default/File.java"
        );

        assertEquals(1, issues.size());
        ReviewIssueEntity issue = issues.get(0);
        assertEquals("SCRIPT", issue.getIssueSource());
        assertEquals("INFO", issue.getSeverity());
        assertEquals("default/File.java", issue.getFilePath());
        assertNull(issue.getStartLine());
        assertNull(issue.getEndLine());
        assertEquals("默认规则", issue.getSummary());
        assertEquals("建议补充测试", issue.getSuggestion());
    }

    @Test
    void returnsEmptyWhenPayloadDoesNotContainIssuesArray() throws Exception {
        List<ReviewIssueEntity> issues = parser.parse(
            "{\"result\":\"ok\"}",
            11L,
            project(),
            rule(),
            null,
            IssueSource.AI,
            "fallback.java"
        );

        assertEquals(0, issues.size());
    }

    private Project project() {
        Project project = new Project();
        project.setId(22L);
        return project;
    }

    private ReviewRuleEntity rule() {
        ReviewRuleEntity rule = new ReviewRuleEntity();
        rule.setId(55L);
        rule.setRuleName("默认规则");
        rule.setRuleType("BUG");
        rule.setSeverity("MINOR");
        return rule;
    }
}
