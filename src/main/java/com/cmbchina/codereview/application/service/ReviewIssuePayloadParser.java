package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.enums.IssueSource;
import com.cmbchina.codereview.common.enums.ReviewIssueStatus;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ReviewIssuePayloadParser {

    private final ObjectMapper objectMapper;

    public ReviewIssuePayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ReviewIssueEntity> parse(String payload,
                                         Long taskId,
                                         Project project,
                                         ReviewRuleEntity rule,
                                         Long skillId,
                                         String skillName,
                                         Long scriptId,
                                         String scriptName,
                                         IssueSource issueSource,
                                         String defaultFilePath) throws Exception {
        String defaultSeverity = rule == null ? "HIGH" : rule.getSeverity();
        String defaultIssueType = rule == null ? "CUSTOM" : rule.getRuleType();
        String defaultSummary = rule == null ? defaultIssueType : rule.getRuleName();
        return parse(payload, taskId, project, rule, skillId, skillName, scriptId, scriptName,
            issueSource, defaultFilePath, defaultSeverity, defaultIssueType, defaultSummary);
    }

    public List<ReviewIssueEntity> parse(String payload,
                                         Long taskId,
                                         Project project,
                                         ReviewRuleEntity rule,
                                         Long skillId,
                                         String skillName,
                                         Long scriptId,
                                         String scriptName,
                                         IssueSource issueSource,
                                         String defaultFilePath,
                                         String defaultSeverity,
                                         String defaultIssueType,
                                         String defaultSummary) throws Exception {
        if (!StringUtils.hasText(payload)) {
            return new ArrayList<>();
        }
        JsonNode root = objectMapper.readTree(payload);
        JsonNode issues = root.isArray() ? root : root.get("issues");
        if (issues == null || !issues.isArray()) {
            return new ArrayList<>();
        }
        List<ReviewIssueEntity> entities = new ArrayList<>();
        for (JsonNode issue : issues) {
            Long ruleId = rule == null ? null : rule.getId();
            String ruleName = rule == null ? null : rule.getRuleName();
            ReviewIssueEntity entity = new ReviewIssueEntity();
            entity.setTaskId(taskId);
            entity.setProjectId(project.getId());
            entity.setRuleId(ruleId);
            entity.setSkillId(skillId);
            entity.setScriptId(scriptId);
            entity.setRuleName(ruleName);
            entity.setSkillName(skillName);
            entity.setScriptName(scriptName);
            entity.setIssueSource(issueSource.name());
            entity.setSeverity(normalizeSeverity(text(issue, "severity", defaultSeverity), defaultSeverity));
            entity.setIssueType(text(issue, "issueType", defaultIssueType));
            entity.setFilePath(firstText(issue, defaultFilePath, "filePath", "filename", "file"));
            Integer startLine = normalizeLine(firstInteger(issue, null, "startLine", "line", "newLine"));
            entity.setStartLine(startLine);
            entity.setEndLine(normalizeLine(firstInteger(issue, startLine, "endLine", "lineEnd", "newEndLine")));
            entity.setSummary(firstText(issue, defaultSummary, "summary", "title", "message", "description"));
            entity.setDetail(firstText(issue, "", "detail", "description", "message"));
            entity.setSuggestion(firstText(issue, "", "suggestion", "suggestedFix", "fix", "recommendation"));
            entity.setCodeSnippet(firstText(issue, "", "codeSnippet", "snippet"));
            entity.setRawResponse(limit(payload, 10000));
            entity.setStatus(ReviewIssueStatus.OPEN.name());
            entities.add(entity);
        }
        return entities;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    private String firstText(JsonNode node, String defaultValue, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().trim().isEmpty()) {
                return value.asText();
            }
        }
        return defaultValue;
    }

    private Integer firstInteger(JsonNode node, Integer defaultValue, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.asInt();
            }
        }
        return defaultValue;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeSeverity(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String upper = value.toUpperCase();
        if ("ERROR".equals(upper) || "MAJOR".equals(upper)) {
            return "HIGH";
        }
        if ("WARNING".equals(upper) || "WARN".equals(upper) || "MINOR".equals(upper)) {
            return "MEDIUM";
        }
        if ("NOTICE".equals(upper) || "INFO".equals(upper)) {
            return "LOW";
        }
        if ("BLOCKER".equals(upper)) {
            return "CRITICAL";
        }
        if ("CRITICAL".equals(upper) || "HIGH".equals(upper) || "MEDIUM".equals(upper) || "LOW".equals(upper)) {
            return upper;
        }
        return defaultValue;
    }

    private Integer normalizeLine(Integer value) {
        return value == null || value < 1 ? null : value;
    }
}
