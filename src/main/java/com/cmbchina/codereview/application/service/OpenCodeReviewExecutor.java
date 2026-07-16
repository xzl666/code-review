package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.enums.OcrReviewMode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.git.GitDiffSummary;
import com.cmbchina.codereview.infrastructure.ocr.OpenCodeReviewProperties;
import com.cmbchina.codereview.infrastructure.ocr.OpenCodeReviewBinaryManager;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenCodeReviewExecutor {

    private final OpenCodeReviewProperties properties;
    private final SystemConfigAppService systemConfigAppService;
    private final OpenCodeReviewResultParser resultParser;
    private final ReviewIssueMapper reviewIssueMapper;
    private final ObjectMapper objectMapper;
    private final OpenCodeReviewUsageCollector usageCollector;
    private final OpenCodeReviewRangeBuilder rangeBuilder;
    private final OpenCodeReviewBinaryManager binaryManager;
    private final IssueAssigneeService issueAssigneeService;

    public OpenCodeReviewExecutor(OpenCodeReviewProperties properties,
                                  SystemConfigAppService systemConfigAppService,
                                  OpenCodeReviewResultParser resultParser,
                                  ReviewIssueMapper reviewIssueMapper,
                                  ObjectMapper objectMapper,
                                  OpenCodeReviewUsageCollector usageCollector,
                                  OpenCodeReviewRangeBuilder rangeBuilder,
                                  OpenCodeReviewBinaryManager binaryManager,
                                  IssueAssigneeService issueAssigneeService) {
        this.properties = properties;
        this.systemConfigAppService = systemConfigAppService;
        this.resultParser = resultParser;
        this.reviewIssueMapper = reviewIssueMapper;
        this.objectMapper = objectMapper;
        this.usageCollector = usageCollector;
        this.rangeBuilder = rangeBuilder;
        this.binaryManager = binaryManager;
        this.issueAssigneeService = issueAssigneeService;
    }

    public OpenCodeReviewExecutionResult execute(Long taskId,
                                                  Project project,
                                                  Path repoDir,
                                                  GitDiffSummary diffSummary,
                                                  ReviewTaskEntity task,
                                                  List<ReviewRuleEntity> rules) {
        OpenCodeReviewExecutionResult result = new OpenCodeReviewExecutionResult();
        OcrReviewMode mode = mode(task.getReviewMode());
        if ((mode == OcrReviewMode.RANGE || mode == OcrReviewMode.YESTERDAY || mode == OcrReviewMode.COMMIT)
            && (diffSummary.getDiffFileCount() == null || diffSummary.getDiffFileCount() == 0)) {
            return result;
        }

        Path ruleFile = null;
        Path stdoutFile = null;
        Path stderrFile = null;
        try {
            ruleFile = writeRuleFile(rules);
            stdoutFile = Files.createTempFile("open-code-review-stdout-", ".json");
            stderrFile = Files.createTempFile("open-code-review-stderr-", ".log");
            OpenCodeReviewRangeBuilder.ReviewRange reviewRange = null;
            if (mode == OcrReviewMode.RANGE || mode == OcrReviewMode.YESTERDAY || mode == OcrReviewMode.COMMIT) {
                reviewRange = rangeBuilder.build(repoDir, diffSummary);
            }
            List<String> command = command(repoDir, mode, reviewRange, ruleFile, task);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(repoDir.toFile());
            builder.redirectOutput(stdoutFile.toFile());
            builder.redirectError(stderrFile.toFile());
            configureEnvironment(builder, systemConfigAppService.getActiveModelConfig(null, null, null));

            result.setInvocationCount(1);
            Process process = builder.start();
            long timeout = positive(properties.getProcessTimeoutMinutes(), 15);
            if (!process.waitFor(timeout, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new BizException(ErrorCode.BIZ_ERROR, "OpenCodeReview 执行超时");
            }
            String stdout = read(stdoutFile);
            String stderr = read(stderrFile);
            if (process.exitValue() != 0) {
                throw new BizException(ErrorCode.BIZ_ERROR,
                    "OpenCodeReview 执行失败（exit=" + process.exitValue() + "）：" + limit(stderr, 1000));
            }
            OpenCodeReviewResultParser.ParsedResult parsed = resultParser.parse(stdout, taskId, project);
            issueAssigneeService.assign(repoDir, diffSummary, parsed.getIssues());
            for (ReviewIssueEntity issue : parsed.getIssues()) {
                reviewIssueMapper.insert(issue);
            }
            result.setIssueCount(parsed.getIssues().size());
            result.setReviewedFileCount(parsed.getFilesReviewed());
            result.getWarnings().addAll(parsed.getWarnings());
            applyUsage(result, parsed);
            if (StringUtils.hasText(stderr)) {
                result.getWarnings().add(limit(stderr.trim(), 500));
            }
            return result;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "OpenCodeReview 兼容适配执行失败：" + exception.getMessage());
        } finally {
            deleteQuietly(ruleFile);
            deleteQuietly(stdoutFile);
            deleteQuietly(stderrFile);
        }
    }

    private void applyUsage(OpenCodeReviewExecutionResult result, OpenCodeReviewResultParser.ParsedResult parsed) {
        OpenCodeReviewUsageCollector.UsageStats stats = usageCollector.collect(parsed.getSessionId());
        result.setAiCallCount(stats.getCallCount());
        result.setAiSuccessCount(stats.getSuccessCount());
        result.setAiFailureCount(stats.getFailureCount());
        result.setInputTokenCount(preferSummary(parsed.getInputTokenCount(), stats.getInputTokenCount()));
        result.setOutputTokenCount(preferSummary(parsed.getOutputTokenCount(), stats.getOutputTokenCount()));
        result.setTotalTokenCount(preferSummary(parsed.getTotalTokenCount(), stats.getTotalTokenCount()));
        result.setCacheReadTokenCount(preferSummary(parsed.getCacheReadTokenCount(), stats.getCacheReadTokenCount()));
        result.setCacheWriteTokenCount(preferSummary(parsed.getCacheWriteTokenCount(), stats.getCacheWriteTokenCount()));
        if (StringUtils.hasText(stats.getWarning())) {
            result.getWarnings().add(stats.getWarning());
        }
    }

    private Long preferSummary(Long summaryValue, Long sessionValue) {
        return summaryValue != null && summaryValue > 0L ? summaryValue : sessionValue;
    }

    private List<String> command(Path repoDir,
                                 OcrReviewMode mode,
                                 OpenCodeReviewRangeBuilder.ReviewRange reviewRange,
                                 Path ruleFile,
                                 ReviewTaskEntity task) {
        List<String> args = new ArrayList<>();
        String executable = binaryManager.resolveCommand();
        args.add(executable);
        args.add(mode == OcrReviewMode.SCAN ? "scan" : "review");
        args.add("--repo");
        args.add(repoDir.toAbsolutePath().toString());
        if (mode == OcrReviewMode.RANGE || mode == OcrReviewMode.YESTERDAY) {
            args.add("--from");
            args.add(reviewRange.getBaseRef());
            args.add("--to");
            args.add(defaultIfBlank(reviewRange.getHeadRef(), "HEAD"));
        } else if (mode == OcrReviewMode.COMMIT) {
            args.add("--commit");
            args.add(defaultIfBlank(reviewRange.getHeadRef(), task.getCommitRef()));
        } else if (mode == OcrReviewMode.SCAN) {
            addOption(args, "--path", task.getScanPath());
            addOption(args, "--exclude", task.getScanExclude());
            if (Integer.valueOf(1).equals(task.getScanNoPlan())) {
                args.add("--no-plan");
            }
            if (task.getMaxTokensBudget() != null && task.getMaxTokensBudget() > 0) {
                args.add("--max-tokens-budget");
                args.add(String.valueOf(task.getMaxTokensBudget()));
            }
        }
        args.add("--format");
        args.add("json");
        args.add("--audience");
        args.add("agent");
        args.add("--concurrency");
        args.add(String.valueOf(positive(properties.getConcurrency(), 4)));
        args.add("--timeout");
        args.add(String.valueOf(positive(properties.getTimeoutMinutes(), 10)));
        args.add("--rule");
        args.add(ruleFile.toAbsolutePath().toString());
        addOption(args, "--background", task.getReviewBackground());
        if (isWindows() && !executable.toLowerCase().endsWith(".exe")) {
            List<String> windowsCommand = new ArrayList<>();
            windowsCommand.add("cmd.exe");
            windowsCommand.add("/d");
            windowsCommand.add("/c");
            windowsCommand.addAll(args);
            return windowsCommand;
        }
        return args;
    }

    private OcrReviewMode mode(String value) {
        try {
            return OcrReviewMode.valueOf(defaultIfBlank(value, OcrReviewMode.RANGE.name()));
        } catch (IllegalArgumentException ignored) {
            return OcrReviewMode.RANGE;
        }
    }

    private void addOption(List<String> args, String name, String value) {
        if (StringUtils.hasText(value)) {
            args.add(name);
            args.add(value.trim());
        }
    }

    private Path writeRuleFile(List<ReviewRuleEntity> rules) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode items = root.putArray("rules");
        for (ReviewRuleEntity rule : rules) {
            if (!StringUtils.hasText(rule.getPromptTemplate())) {
                continue;
            }
            ObjectNode item = items.addObject();
            item.put("path", defaultIfBlank(rule.getPathPattern(), "**/*"));
            item.put("rule", rule.getPromptTemplate());
            item.put("merge_system_rule", rule.getMergeSystemRule() == null || rule.getMergeSystemRule() == 1);
        }
        ObjectNode fallback = items.addObject();
        fallback.put("path", "**/*");
        fallback.put("rule", "请使用中文输出评审意见，聚焦真实缺陷并给出可执行的修复建议。");
        fallback.put("merge_system_rule", true);
        Path file = Files.createTempFile("open-code-review-rules-", ".json");
        Files.write(file, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        return file;
    }

    private void configureEnvironment(ProcessBuilder builder, ActiveModelConfig model) {
        if (!StringUtils.hasText(model.getApiKey()) || !StringUtils.hasText(model.getBaseUrl())
            || !StringUtils.hasText(model.getModelName())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "OpenCodeReview 需要完整的模型 URL、模型名称和 API Key");
        }
        builder.environment().put("OCR_LLM_URL", model.getBaseUrl());
        builder.environment().put("OCR_LLM_TOKEN", model.getApiKey());
        builder.environment().put("OCR_LLM_MODEL", model.getModelName());
        builder.environment().put("OCR_LLM_PROTOCOL", "openai");
        builder.environment().put("OCR_USE_ANTHROPIC", "false");
        builder.environment().put("OCR_NO_UPDATE", "1");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private int positive(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    private void deleteQuietly(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
        }
    }
}
