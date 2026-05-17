package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.enums.IssueSource;
import com.cmbchina.codereview.common.enums.ReviewIssueStatus;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.ai.DeepSeekClient;
import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.AiSkillEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AiReviewExecutor {

    private final DeepSeekClient deepSeekClient;

    private final ReviewIssueMapper reviewIssueMapper;

    private final ObjectMapper objectMapper;

    public AiReviewExecutor(DeepSeekClient deepSeekClient,
                            ReviewIssueMapper reviewIssueMapper,
                            ObjectMapper objectMapper) {
        this.deepSeekClient = deepSeekClient;
        this.reviewIssueMapper = reviewIssueMapper;
        this.objectMapper = objectMapper;
    }

    public int execute(Long taskId,
                       Project project,
                       ReviewRuleEntity rule,
                       AiSkillEntity skill,
                       DiffChunk chunk,
                       String branch,
                       Integer reviewDays) {
        String arguments = deepSeekClient.review(project, rule, skill, chunk, branch, reviewDays);
        return saveIssues(taskId, project, rule, skill, chunk, arguments);
    }

    private int saveIssues(Long taskId,
                           Project project,
                           ReviewRuleEntity rule,
                           AiSkillEntity skill,
                           DiffChunk chunk,
                           String arguments) {
        try {
            JsonNode root = objectMapper.readTree(arguments);
            JsonNode issues = root.isArray() ? root : root.get("issues");
            if (issues == null || !issues.isArray()) {
                return 0;
            }
            int count = 0;
            for (JsonNode issue : issues) {
                ReviewIssueEntity entity = new ReviewIssueEntity();
                entity.setTaskId(taskId);
                entity.setProjectId(project.getId());
                entity.setRuleId(rule.getId());
                entity.setSkillId(skill.getId());
                entity.setIssueSource(IssueSource.AI.name());
                entity.setSeverity(text(issue, "severity", rule.getSeverity()));
                entity.setIssueType(text(issue, "issueType", rule.getRuleType()));
                entity.setFilePath(text(issue, "filePath", chunk.getFilePath()));
                entity.setStartLine(integer(issue, "startLine", null));
                entity.setEndLine(integer(issue, "endLine", integer(issue, "startLine", null)));
                entity.setSummary(text(issue, "summary", text(issue, "title", rule.getRuleName())));
                entity.setDetail(text(issue, "detail", text(issue, "description", "")));
                entity.setSuggestion(text(issue, "suggestion", ""));
                entity.setCodeSnippet(text(issue, "codeSnippet", ""));
                entity.setRawResponse(limit(arguments, 10000));
                entity.setStatus(ReviewIssueStatus.OPEN.name());
                reviewIssueMapper.insert(entity);
                count++;
            }
            return count;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI function arguments are not valid review issue JSON: " + exception.getMessage());
        }
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    private Integer integer(JsonNode node, String field, Integer defaultValue) {
        JsonNode value = node.get(field);
        return value == null || !value.canConvertToInt() ? defaultValue : value.asInt();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
