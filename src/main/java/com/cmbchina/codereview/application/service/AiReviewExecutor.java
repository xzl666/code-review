package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.enums.IssueSource;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.ai.DeepSeekClient;
import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.AiSkillEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiReviewExecutor {

    private final DeepSeekClient deepSeekClient;

    private final ReviewIssueMapper reviewIssueMapper;

    private final ReviewIssuePayloadParser reviewIssuePayloadParser;

    public AiReviewExecutor(DeepSeekClient deepSeekClient,
                            ReviewIssueMapper reviewIssueMapper,
                            ReviewIssuePayloadParser reviewIssuePayloadParser) {
        this.deepSeekClient = deepSeekClient;
        this.reviewIssueMapper = reviewIssueMapper;
        this.reviewIssuePayloadParser = reviewIssuePayloadParser;
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
            List<ReviewIssueEntity> issues = reviewIssuePayloadParser.parse(
                arguments,
                taskId,
                project,
                rule,
                skill.getId(),
                IssueSource.AI,
                chunk.getFilePath()
            );
            for (ReviewIssueEntity entity : issues) {
                reviewIssueMapper.insert(entity);
            }
            return issues.size();
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "AI function arguments are not valid review issue JSON: " + exception.getMessage());
        }
    }
}
