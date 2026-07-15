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

    private final AiIssueLocationNormalizer aiIssueLocationNormalizer;

    public AiReviewExecutor(DeepSeekClient deepSeekClient,
                            ReviewIssueMapper reviewIssueMapper,
                            ReviewIssuePayloadParser reviewIssuePayloadParser,
                            AiIssueLocationNormalizer aiIssueLocationNormalizer) {
        this.deepSeekClient = deepSeekClient;
        this.reviewIssueMapper = reviewIssueMapper;
        this.reviewIssuePayloadParser = reviewIssuePayloadParser;
        this.aiIssueLocationNormalizer = aiIssueLocationNormalizer;
    }

    public int execute(Long taskId,
                       Project project,
                       ReviewRuleEntity rule,
                       AiSkillEntity skill,
                       DiffChunk chunk,
                       String branch,
                       Integer reviewDays) {
        String arguments = deepSeekClient.review(project, rule, skill, chunk, branch, reviewDays);
        return saveIssues(taskId, project, rule, skill, chunk, branch, reviewDays, arguments);
    }

    private int saveIssues(Long taskId,
                           Project project,
                           ReviewRuleEntity rule,
                           AiSkillEntity skill,
                           DiffChunk chunk,
                           String branch,
                           Integer reviewDays,
                           String arguments) {
        try {
            return saveParsedIssues(taskId, project, rule, skill, chunk, arguments);
        } catch (Exception firstException) {
            try {
                String repairedArguments = deepSeekClient.repairReviewArguments(
                    arguments,
                    firstException.getMessage(),
                    project,
                    rule,
                    skill,
                    chunk,
                    branch,
                    reviewDays
                );
                return saveParsedIssues(taskId, project, rule, skill, chunk, repairedArguments);
            } catch (Exception secondException) {
                throw new BizException(ErrorCode.BIZ_ERROR, "AI function arguments are not valid review issue JSON after repair: "
                    + secondException.getMessage() + "; original parse error: " + firstException.getMessage());
            }
        }
    }

    private int saveParsedIssues(Long taskId,
                                 Project project,
                                 ReviewRuleEntity rule,
                                 AiSkillEntity skill,
                                 DiffChunk chunk,
                                 String arguments) throws Exception {
            List<ReviewIssueEntity> issues = reviewIssuePayloadParser.parse(
                arguments,
                taskId,
                project,
                rule,
                skill.getId(),
                skill.getSkillName(),
                null,
                null,
                IssueSource.AI,
                chunk.getFilePath()
            );
            for (ReviewIssueEntity entity : issues) {
                aiIssueLocationNormalizer.normalize(entity, chunk);
                reviewIssueMapper.insert(entity);
            }
            return issues.size();
    }
}
