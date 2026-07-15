package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.enums.IssueSource;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.git.GitDiffSummary;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
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

    public ScriptRuleExecutionResult execute(Long taskId,
                                             Project project,
                                             List<ScriptRuleEntity> scripts,
                                             GitDiffSummary diffSummary,
                                             String branch,
                                             Integer reviewDays) {
        ScriptRuleExecutionResult executionResult = new ScriptRuleExecutionResult();
        if (scripts == null || scripts.isEmpty()) {
            return executionResult;
        }
        for (ScriptRuleEntity script : scripts) {
            try {
                String stdout = runScript(project, script, diffSummary, branch, reviewDays);
                executionResult.addIssueCount(saveIssues(taskId, project, script, stdout));
            } catch (Exception exception) {
                executionResult.addWarning(limit("Script review skipped for "
                    + sourceName(script.getScriptName(), script.getId()) + ": " + exception.getMessage(), 500));
            }
        }
        return executionResult;
    }

    private String runScript(Project project,
                             ScriptRuleEntity script,
                             GitDiffSummary diffSummary,
                             String branch,
                             Integer reviewDays) {
        try {
            ScriptExecutionRequest request = new ScriptExecutionRequest();
            request.setLanguage("PYTHON");
            request.setContent(script.getScriptContent());
            request.setInputJson(inputJson(project, diffSummary, branch, reviewDays));
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
            validateIssueJson(result.getStdout());
            return limit(result.getStdout(), 200000);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "script execution error: " + exception.getMessage());
        }
    }

    private int saveIssues(Long taskId, Project project, ScriptRuleEntity script, String stdout) {
        if (!StringUtils.hasText(stdout)) {
            return 0;
        }
        try {
            List<ReviewIssueEntity> issues = reviewIssuePayloadParser.parse(
                stdout,
                taskId,
                project,
                null,
                null,
                null,
                script.getId(),
                script.getScriptName(),
                IssueSource.SCRIPT,
                "",
                defaultIfBlank(script.getSeverity(), "MAJOR"),
                defaultIfBlank(script.getRuleType(), "CUSTOM"),
                defaultIfBlank(script.getScriptName(), "脚本规则")
            );
            for (ReviewIssueEntity entity : issues) {
                reviewIssueMapper.insert(entity);
            }
            return issues.size();
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "script output is not valid review issue JSON: " + exception.getMessage());
        }
    }

    private String inputJson(Project project, GitDiffSummary diffSummary, String branch, Integer reviewDays) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> projectInput = new LinkedHashMap<>();
        projectInput.put("id", project.getId());
        projectInput.put("name", project.getProjectName());
        projectInput.put("code", project.getProjectCode());
        projectInput.put("type", project.getProjectType());
        input.put("project", projectInput);
        input.put("branch", branch);
        input.put("reviewDays", reviewDays);
        input.put("commitCount", diffSummary.getCommitCount());
        input.put("diffFileCount", diffSummary.getDiffFileCount());
        input.put("filePaths", diffSummary.getFilePaths());
        input.put("diffContent", diffSummary.getDiffContent());
        input.put("files", diffSummary.getFiles());
        return objectMapper.writeValueAsString(input);
    }

    private void validateIssueJson(String stdout) throws Exception {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(stdout);
        com.fasterxml.jackson.databind.JsonNode issues = root.isArray() ? root : root.get("issues");
        if (issues == null || !issues.isArray()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "script output must be JSON with issues array");
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String sourceName(String name, Long id) {
        return StringUtils.hasText(name) ? name + " (#" + id + ")" : "#" + id;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
