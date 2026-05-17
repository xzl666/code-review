package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.enums.IssueSource;
import com.cmbchina.codereview.common.enums.ReviewIssueStatus;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.git.GitDiffSummary;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ScriptRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ScriptReviewExecutor {

    private final ReviewIssueMapper reviewIssueMapper;

    private final ObjectMapper objectMapper;

    public ScriptReviewExecutor(ReviewIssueMapper reviewIssueMapper, ObjectMapper objectMapper) {
        this.reviewIssueMapper = reviewIssueMapper;
        this.objectMapper = objectMapper;
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
            Path workDir = Files.createTempDirectory("code-review-engine-script-");
            Path scriptPath = writeScript(workDir, script);
            ProcessBuilder builder = new ProcessBuilder(command(script.getScriptLanguage(), scriptPath));
            builder.directory(workDir.toFile());
            Process process = builder.start();
            process.getOutputStream().write(inputJson(project, diffSummary, branch).getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            int timeout = script.getTimeoutSeconds() == null ? 30 : script.getTimeoutSeconds();
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException(ErrorCode.BIZ_ERROR, "script execution timeout: " + script.getScriptName());
            }
            String stdout = read(process.getInputStream());
            String stderr = read(process.getErrorStream());
            if (process.exitValue() != 0) {
                throw new BizException(ErrorCode.BIZ_ERROR, "script execution failed: " + script.getScriptName() + "; " + stderr);
            }
            return limit(stdout, 200000);
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
            JsonNode root = objectMapper.readTree(stdout);
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
                entity.setSkillId(rule.getSkillId());
                entity.setIssueSource(IssueSource.SCRIPT.name());
                entity.setSeverity(text(issue, "severity", rule.getSeverity()));
                entity.setIssueType(text(issue, "issueType", rule.getRuleType()));
                entity.setFilePath(text(issue, "filePath", ""));
                entity.setStartLine(integer(issue, "startLine", null));
                entity.setEndLine(integer(issue, "endLine", integer(issue, "startLine", null)));
                entity.setSummary(text(issue, "summary", text(issue, "title", rule.getRuleName())));
                entity.setDetail(text(issue, "detail", ""));
                entity.setSuggestion(text(issue, "suggestion", ""));
                entity.setCodeSnippet(text(issue, "codeSnippet", ""));
                entity.setRawResponse(limit(stdout, 10000));
                entity.setStatus(ReviewIssueStatus.OPEN.name());
                reviewIssueMapper.insert(entity);
                count++;
            }
            return count;
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

    private Path writeScript(Path workDir, ScriptRuleEntity script) throws Exception {
        String suffix = "SHELL".equals(script.getScriptLanguage()) ? ".sh" : ("PYTHON".equals(script.getScriptLanguage()) ? ".py" : ".js");
        Path scriptPath = workDir.resolve("script" + suffix);
        Files.write(scriptPath, script.getScriptContent().getBytes(StandardCharsets.UTF_8));
        return scriptPath;
    }

    private List<String> command(String language, Path scriptPath) {
        if ("PYTHON".equals(language)) {
            return Arrays.asList("python", scriptPath.toAbsolutePath().toString());
        }
        if ("NODE".equals(language)) {
            return Arrays.asList("node", scriptPath.toAbsolutePath().toString());
        }
        if (File.separatorChar == '\\') {
            return Arrays.asList("cmd", "/c", scriptPath.toAbsolutePath().toString());
        }
        return Arrays.asList("sh", scriptPath.toAbsolutePath().toString());
    }

    private String read(java.io.InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
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
