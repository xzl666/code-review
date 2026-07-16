package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cmbchina.codereview.common.enums.ReviewTaskStatus;
import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.common.enums.OcrReviewMode;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.infrastructure.git.GitDiffService;
import com.cmbchina.codereview.infrastructure.git.GitDiffSummary;
import com.cmbchina.codereview.infrastructure.git.LocalRepositoryManager;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewRuleMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewTaskMapper;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReviewEngineAppService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewIssueMapper reviewIssueMapper;
    private final ReviewRuleMapper reviewRuleMapper;
    private final ProjectRepository projectRepository;
    private final LocalRepositoryManager localRepositoryManager;
    private final GitDiffService gitDiffService;
    private final OpenCodeReviewExecutor openCodeReviewExecutor;
    private final ZhaohuNotificationService zhaohuNotificationService;
    private final ReviewIssueFingerprintService reviewIssueFingerprintService;
    private final ReviewReportAppService reviewReportAppService;
    private final Executor reviewTaskExecutor;

    public ReviewEngineAppService(ReviewTaskMapper reviewTaskMapper,
                                  ReviewIssueMapper reviewIssueMapper,
                                  ReviewRuleMapper reviewRuleMapper,
                                  ProjectRepository projectRepository,
                                  LocalRepositoryManager localRepositoryManager,
                                  GitDiffService gitDiffService,
                                  OpenCodeReviewExecutor openCodeReviewExecutor,
                                  ZhaohuNotificationService zhaohuNotificationService,
                                  ReviewIssueFingerprintService reviewIssueFingerprintService,
                                  ReviewReportAppService reviewReportAppService,
                                  @Qualifier("reviewTaskExecutor") Executor reviewTaskExecutor) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewIssueMapper = reviewIssueMapper;
        this.reviewRuleMapper = reviewRuleMapper;
        this.projectRepository = projectRepository;
        this.localRepositoryManager = localRepositoryManager;
        this.gitDiffService = gitDiffService;
        this.openCodeReviewExecutor = openCodeReviewExecutor;
        this.zhaohuNotificationService = zhaohuNotificationService;
        this.reviewIssueFingerprintService = reviewIssueFingerprintService;
        this.reviewReportAppService = reviewReportAppService;
        this.reviewTaskExecutor = reviewTaskExecutor;
    }

    public void submit(Long taskId) {
        reviewTaskExecutor.execute(() -> execute(taskId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void execute(Long taskId) {
        ReviewTaskEntity task = reviewTaskMapper.selectById(taskId);
        if (task == null || !ReviewTaskStatus.PENDING.name().equals(task.getStatus())) {
            return;
        }
        markRunning(taskId);
        try {
            Project project = projectRepository.findById(task.getProjectId());
            if (project == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "project not found");
            }
            String branch = defaultIfBlank(task.getReviewBranch(), project.getDefaultBranch());
            Path repoDir = localRepositoryManager.prepare(project, branch);
            GitDiffSummary diffSummary = prepareDiff(repoDir, task);
            OpenCodeReviewExecutionResult engineResult = executeOpenCodeReview(taskId, project, repoDir, diffSummary, task);
            reviewIssueFingerprintService.applyHistoricalIgnoredIssues(taskId, project.getId());
            TaskIssueCounters counters = countIssues(taskId);
            markSuccess(taskId, diffSummary, counters, engineResult);
            generateReport(taskId);
            ReviewTaskEntity completedTask = reviewTaskMapper.selectById(taskId);
            zhaohuNotificationService.notifyDailyReviewCompleted(completedTask);
        } catch (Exception exception) {
            markFailed(taskId, exception.getMessage());
            generateReport(taskId);
            ReviewTaskEntity failedTask = reviewTaskMapper.selectById(taskId);
            zhaohuNotificationService.notifyDailyReviewCompleted(failedTask);
        }
    }

    private void generateReport(Long taskId) {
        try {
            reviewReportAppService.generate(taskId);
        } catch (Exception ignored) {
            // Report generation must not hide the review task result.
        }
    }

    private OpenCodeReviewExecutionResult executeOpenCodeReview(Long taskId,
                                                                Project project,
                                                                Path repoDir,
                                                                GitDiffSummary diffSummary,
                                                                ReviewTaskEntity task) {
        LambdaQueryWrapper<ReviewRuleEntity> wrapper = new LambdaQueryWrapper<ReviewRuleEntity>()
            .eq(ReviewRuleEntity::getStatus, BaseStatus.ENABLED.getValue())
            .orderByAsc(ReviewRuleEntity::getSortOrder)
            .orderByAsc(ReviewRuleEntity::getId);
        List<ReviewRuleEntity> rules = reviewRuleMapper.selectList(wrapper);
        return openCodeReviewExecutor.execute(taskId, project, repoDir, diffSummary, task, rules);
    }

    private GitDiffSummary prepareDiff(Path repoDir, ReviewTaskEntity task) {
        if (task.getReviewStartTime() != null && task.getReviewEndTime() != null) {
            return gitDiffService.summarizeTimeRange(repoDir, task.getReviewBranch(),
                task.getReviewStartTime(), task.getReviewEndTime(), ZoneId.of("Asia/Shanghai"));
        }
        OcrReviewMode mode = reviewMode(task.getReviewMode());
        switch (mode) {
            case COMMIT:
                return gitDiffService.summarizeCommit(repoDir,
                    localRepositoryManager.ensureRef(repoDir, task.getCommitRef()));
            case WORKSPACE:
                return gitDiffService.summarizeWorkspace(repoDir);
            case SCAN:
                return gitDiffService.emptySummary();
            case RANGE:
            default:
                if (!StringUtils.hasText(task.getBaseRef()) || !StringUtils.hasText(task.getTargetRef())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "分支区间检视必须指定起始版本和目标版本");
                }
                String baseRef = localRepositoryManager.ensureRef(repoDir, task.getBaseRef());
                String targetRef = localRepositoryManager.ensureRef(repoDir, task.getTargetRef());
                return gitDiffService.summarizeRange(repoDir, baseRef, targetRef);
        }
    }

    private OcrReviewMode reviewMode(String value) {
        try {
            return OcrReviewMode.valueOf(defaultIfBlank(value, OcrReviewMode.RANGE.name()));
        } catch (IllegalArgumentException ignored) {
            return OcrReviewMode.RANGE;
        }
    }

    private TaskIssueCounters countIssues(Long taskId) {
        TaskIssueCounters counters = new TaskIssueCounters();
        counters.issueCount = reviewIssueMapper.selectCount(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getTaskId, taskId)).intValue();
        counters.criticalCount = countSeverity(taskId, "CRITICAL");
        counters.highCount = countSeverity(taskId, "HIGH");
        counters.mediumCount = countSeverity(taskId, "MEDIUM");
        counters.lowCount = countSeverity(taskId, "LOW");
        return counters;
    }

    private Integer countSeverity(Long taskId, String severity) {
        return reviewIssueMapper.selectCount(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getTaskId, taskId)
            .eq(ReviewIssueEntity::getSeverity, severity)).intValue();
    }

    private void markRunning(Long taskId) {
        LambdaUpdateWrapper<ReviewTaskEntity> wrapper = new LambdaUpdateWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getId, taskId)
            .eq(ReviewTaskEntity::getStatus, ReviewTaskStatus.PENDING.name())
            .set(ReviewTaskEntity::getStatus, ReviewTaskStatus.RUNNING.name())
            .set(ReviewTaskEntity::getStartTime, LocalDateTime.now())
            .set(ReviewTaskEntity::getErrorMessage, null);
        reviewTaskMapper.update(null, wrapper);
    }

    private void markSuccess(Long taskId,
                             GitDiffSummary diffSummary,
                             TaskIssueCounters counters,
                             OpenCodeReviewExecutionResult engineResult) {
        LambdaUpdateWrapper<ReviewTaskEntity> wrapper = new LambdaUpdateWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getId, taskId)
            .set(ReviewTaskEntity::getStatus, ReviewTaskStatus.SUCCESS.name())
            .set(ReviewTaskEntity::getCommitCount, diffSummary.getCommitCount())
            .set(ReviewTaskEntity::getDiffFileCount,
                engineResult.getReviewedFileCount() != null && engineResult.getReviewedFileCount() > 0
                    ? engineResult.getReviewedFileCount() : diffSummary.getDiffFileCount())
            .set(ReviewTaskEntity::getIssueCount, counters.issueCount)
            .set(ReviewTaskEntity::getCriticalCount, counters.criticalCount)
            .set(ReviewTaskEntity::getHighCount, counters.highCount)
            .set(ReviewTaskEntity::getMediumCount, counters.mediumCount)
            .set(ReviewTaskEntity::getLowCount, counters.lowCount)
            .set(ReviewTaskEntity::getAiCallCount, engineResult.getAiCallCount())
            .set(ReviewTaskEntity::getAiSuccessCount, engineResult.getAiSuccessCount())
            .set(ReviewTaskEntity::getAiFailureCount, engineResult.getAiFailureCount())
            .set(ReviewTaskEntity::getInputTokenCount, engineResult.getInputTokenCount())
            .set(ReviewTaskEntity::getOutputTokenCount, engineResult.getOutputTokenCount())
            .set(ReviewTaskEntity::getTotalTokenCount, engineResult.getTotalTokenCount())
            .set(ReviewTaskEntity::getCacheReadTokenCount, engineResult.getCacheReadTokenCount())
            .set(ReviewTaskEntity::getCacheWriteTokenCount, engineResult.getCacheWriteTokenCount())
            .set(ReviewTaskEntity::getSkippedCommitCount, diffSummary.getSkippedCommitCount())
            .set(ReviewTaskEntity::getSkippedFileCount, diffSummary.getSkippedFileCount())
            .set(ReviewTaskEntity::getEndTime, LocalDateTime.now())
            .set(ReviewTaskEntity::getWarningMessage, warningMessage(diffSummary, engineResult.getWarnings()))
            .set(ReviewTaskEntity::getErrorMessage, null);
        reviewTaskMapper.update(null, wrapper);
    }

    private void markFailed(Long taskId, String message) {
        LambdaUpdateWrapper<ReviewTaskEntity> wrapper = new LambdaUpdateWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getId, taskId)
            .set(ReviewTaskEntity::getStatus, ReviewTaskStatus.FAILED.name())
            .set(ReviewTaskEntity::getEndTime, LocalDateTime.now())
            .set(ReviewTaskEntity::getWarningMessage, null)
            .set(ReviewTaskEntity::getErrorMessage, limit(message, 1000));
        reviewTaskMapper.update(null, wrapper);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String warningMessage(GitDiffSummary diffSummary, List<String> engineWarnings) {
        List<String> warnings = new ArrayList<>();
        if (diffSummary.getWarnings() != null && !diffSummary.getWarnings().isEmpty()) {
            warnings.addAll(diffSummary.getWarnings());
        }
        if (engineWarnings != null && !engineWarnings.isEmpty()) {
            warnings.addAll(engineWarnings);
        }
        if (warnings.isEmpty()) {
            return null;
        }
        return limit(String.join(" ", warnings), 1000);
    }

    private static class TaskIssueCounters {
        private Integer issueCount = 0;
        private Integer criticalCount = 0;
        private Integer highCount = 0;
        private Integer mediumCount = 0;
        private Integer lowCount = 0;
    }
}
