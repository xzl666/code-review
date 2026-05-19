package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.enums.IssueSource;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.git.GitDiffSummary;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ScriptRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ScriptReviewExecutor {

    private final ReviewIssueMapper reviewIssueMapper;

    private final ObjectMapper objectMapper;

    private final ReviewIssuePayloadParser reviewIssuePayloadParser;

    private final ScriptSandboxExecutor scriptSandboxExecutor;

    public ScriptReviewExecutor(ReviewIssueMapper reviewIssueMapper,
                                ObjectMapper objectMapper,
                                ReviewIssuePayloadParser reviewIssuePayloadParser,
                                ScriptSandboxExecutor scriptSandboxExecutor) {
        this.reviewIssueMapper = reviewIssueMapper;
        this.objectMapper = objectMapper;
        this.reviewIssuePayloadParser = reviewIssuePayloadParser;
        this.scriptSandboxExecutor = scriptSandboxExecutor;
    }

    public int execute(Long taskId,
                       Project project,
                       ReviewRuleEntity rule,
                       ScriptRuleEntity script,
                       GitDiffSummary diffSummary,
                       String branch) {
        String stdout = runScript(project, script, diffSummary, branch);
        return saveIssues(taskId, project, rule, stdout);
    }

    private String runScript(Project project, ScriptRuleEntity script, GitDiffSummary diffSummary, String branch) {
        try {
            ScriptExecutionRequest request = new ScriptExecutionRequest();
            request.setLanguage(script.getScriptLanguage());
            request.setContent(script.getScriptContent());
            request.setInputJson(inputJson(project, diffSummary, branch));
            request.setTimeoutSeconds(script.getTimeoutSeconds());
            request.setMaxOutputChars(200000);
            ScriptExecutionResult result = scriptSandboxExecutor.execute(request);
            if (Boolean.TRUE.equals(result.getTimeout())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "script execution timeout: " + script.getScriptName());
            }
            if (Boolean.TRUE.equals(result.getSecurityBlocked())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "script execution blocked by sandbox: " + script.getScriptName() + "; " + result.getStderr());
            }
            if (!Boolean.TRUE.equals(result.getSuccess())) {
                throw new BizException(ErrorCode.BIZ_ERROR, "script execution failed: " + script.getScriptName() + "; " + result.getStderr());
            }
            return limit(result.getStdout(), 200000);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "script execution error: " + exception.getMessage());
        }
    }

    private int saveIssues(Long taskId, Project project, ReviewRuleEntity rule, String stdout) {
        if (!StringUtils.hasText(stdout)) {
            return 0;
        }
        try {
            List<ReviewIssueEntity> issues = reviewIssuePayloadParser.parse(
                stdout,
                taskId,
                project,
                rule,
                rule.getSkillId(),
                IssueSource.SCRIPT,
                ""
            );
            for (ReviewIssueEntity entity : issues) {
                reviewIssueMapper.insert(entity);
            }
            return issues.size();
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "script output is not valid review issue JSON: " + exception.getMessage());
        }
    }

    private String inputJson(Project project, GitDiffSummary diffSummary, String branch) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("projectId", project.getId());
        input.put("projectName", project.getProjectName());
        input.put("projectCode", project.getProjectCode());
        input.put("projectType", project.getProjectType());
        input.put("branch", branch);
        input.put("reviewDays", project.getReviewDays());
        input.put("commitCount", diffSummary.getCommitCount());
        input.put("diffFileCount", diffSummary.getDiffFileCount());
        input.put("filePaths", diffSummary.getFilePaths());
        input.put("diffContent", diffSummary.getDiffContent());
        return objectMapper.writeValueAsString(input);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
