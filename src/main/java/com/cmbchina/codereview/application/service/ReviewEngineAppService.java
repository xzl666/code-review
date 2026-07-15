package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cmbchina.codereview.common.enums.ReviewTaskStatus;
import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.common.enums.RuleKind;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.infrastructure.ai.DeepSeekProperties;
import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.git.DiffChunkService;
import com.cmbchina.codereview.infrastructure.git.GitDiffService;
import com.cmbchina.codereview.infrastructure.git.GitDiffSummary;
import com.cmbchina.codereview.infrastructure.git.LocalRepositoryManager;
import com.cmbchina.codereview.infrastructure.persistence.entity.AiSkillEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ScriptRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.AiSkillMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewRuleMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ScriptRuleMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewTaskMapper;
import java.nio.file.Path;
import java.time.LocalDateTime;
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
    private final AiSkillMapper aiSkillMapper;
    private final ReviewRuleMapper reviewRuleMapper;
    private final ScriptRuleMapper scriptRuleMapper;
    private final ProjectRepository projectRepository;
    private final SystemConfigAppService systemConfigAppService;
    private final LocalRepositoryManager localRepositoryManager;
    private final GitDiffService gitDiffService;
    private final DiffChunkService diffChunkService;
    private final DeepSeekProperties deepSeekProperties;
    private final AiReviewExecutor aiReviewExecutor;
    private final ScriptReviewExecutor scriptReviewExecutor;
    private final AiSkillRuleMatcher aiSkillRuleMatcher;
    private final NotificationDispatchService notificationDispatchService;
    private final ReviewIssueFingerprintService reviewIssueFingerprintService;
    private final ReviewReportAppService reviewReportAppService;
    private final Executor reviewTaskExecutor;

    public ReviewEngineAppService(ReviewTaskMapper reviewTaskMapper,
                                  ReviewIssueMapper reviewIssueMapper,
                                  AiSkillMapper aiSkillMapper,
                                  ReviewRuleMapper reviewRuleMapper,
                                  ScriptRuleMapper scriptRuleMapper,
                                  ProjectRepository projectRepository,
                                  SystemConfigAppService systemConfigAppService,
                                  LocalRepositoryManager localRepositoryManager,
                                  GitDiffService gitDiffService,
                                  DiffChunkService diffChunkService,
                                  DeepSeekProperties deepSeekProperties,
                                  AiReviewExecutor aiReviewExecutor,
                                  ScriptReviewExecutor scriptReviewExecutor,
                                  AiSkillRuleMatcher aiSkillRuleMatcher,
                                  NotificationDispatchService notificationDispatchService,
                                  ReviewIssueFingerprintService reviewIssueFingerprintService,
                                  ReviewReportAppService reviewReportAppService,
                                  @Qualifier("reviewTaskExecutor") Executor reviewTaskExecutor) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewIssueMapper = reviewIssueMapper;
        this.aiSkillMapper = aiSkillMapper;
        this.reviewRuleMapper = reviewRuleMapper;
        this.scriptRuleMapper = scriptRuleMapper;
        this.projectRepository = projectRepository;
        this.systemConfigAppService = systemConfigAppService;
        this.localRepositoryManager = localRepositoryManager;
        this.gitDiffService = gitDiffService;
        this.diffChunkService = diffChunkService;
        this.deepSeekProperties = deepSeekProperties;
        this.aiReviewExecutor = aiReviewExecutor;
        this.scriptReviewExecutor = scriptReviewExecutor;
        this.aiSkillRuleMatcher = aiSkillRuleMatcher;
        this.notificationDispatchService = notificationDispatchService;
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
            Path repoDir = localRepositoryManager.prepare(project, branch, resolveToken(project));
            GitDiffSummary diffSummary = gitDiffService.summarize(repoDir, task.getReviewDays());
            AiRuleExecutionResult aiRuleResult = executeAiRules(taskId, project, diffSummary, branch, task.getReviewDays());
            ScriptRuleExecutionResult scriptRuleResult = executeScriptRules(taskId, project, diffSummary, branch, task.getReviewDays());
            reviewIssueFingerprintService.applyHistoricalIgnoredIssues(taskId, project.getId());
            TaskIssueCounters counters = countIssues(taskId);
            markSuccess(taskId, diffSummary, counters, aiRuleResult, scriptRuleResult);
            generateReport(taskId);
            notificationDispatchService.notifyTaskSuccess(reviewTaskMapper.selectById(taskId));
        } catch (Exception exception) {
            markFailed(taskId, exception.getMessage());
            generateReport(taskId);
            notificationDispatchService.notifyTaskFailed(reviewTaskMapper.selectById(taskId));
        }
    }

    private void generateReport(Long taskId) {
        try {
            reviewReportAppService.generate(taskId);
        } catch (Exception ignored) {
            // Report generation must not hide the review task result.
        }
    }

    private String resolveToken(Project project) {
        if (StringUtils.hasText(project.getProjectToken())) {
            return project.getProjectToken();
        }
        if (project.getUseDefaultToken() != null && project.getUseDefaultToken() == 1) {
            return systemConfigAppService.getDefaultGiteeToken();
        }
        return null;
    }

    private ScriptRuleExecutionResult executeScriptRules(Long taskId,
                                                         Project project,
                                                         GitDiffSummary diffSummary,
                                                         String branch,
                                                         Integer reviewDays) {
        LambdaQueryWrapper<ScriptRuleEntity> wrapper = new LambdaQueryWrapper<ScriptRuleEntity>()
            .eq(ScriptRuleEntity::getStatus, BaseStatus.ENABLED.getValue())
            .and(rule -> rule.eq(ScriptRuleEntity::getProjectType, "ALL")
                .or()
                .eq(ScriptRuleEntity::getProjectType, project.getProjectType()))
            .orderByAsc(ScriptRuleEntity::getSortOrder)
            .orderByAsc(ScriptRuleEntity::getId);
        List<ScriptRuleEntity> scripts = scriptRuleMapper.selectList(wrapper);
        return scriptReviewExecutor.execute(taskId, project, scripts, diffSummary, branch, reviewDays);
    }

    private AiRuleExecutionResult executeAiRules(Long taskId, Project project, GitDiffSummary diffSummary, String branch, Integer reviewDays) {
        AiRuleExecutionResult result = new AiRuleExecutionResult();
        LambdaQueryWrapper<ReviewRuleEntity> wrapper = new LambdaQueryWrapper<ReviewRuleEntity>()
            .eq(ReviewRuleEntity::getStatus, BaseStatus.ENABLED.getValue())
            .eq(ReviewRuleEntity::getRuleKind, RuleKind.AI.name())
            .and(rule -> rule.eq(ReviewRuleEntity::getProjectType, "ALL")
                .or()
                .eq(ReviewRuleEntity::getProjectType, project.getProjectType()))
            .orderByAsc(ReviewRuleEntity::getSortOrder)
            .orderByAsc(ReviewRuleEntity::getId);
        List<ReviewRuleEntity> rules = reviewRuleMapper.selectList(wrapper);
        if (rules.isEmpty()) {
            return result;
        }
        List<DiffChunk> chunks = diffChunkService.split(diffSummary, deepSeekProperties.getMaxDiffCharsPerRequest());
        if (chunks.isEmpty()) {
            return result;
        }
        for (ReviewRuleEntity rule : rules) {
            if (rule.getSkillId() == null) {
                continue;
            }
            AiSkillEntity skill = aiSkillMapper.selectById(rule.getSkillId());
            if (skill == null || skill.getStatus() == null || skill.getStatus() != BaseStatus.ENABLED.getValue()) {
                continue;
            }
            if (!aiSkillRuleMatcher.appliesToProject(skill, project)) {
                continue;
            }
            for (DiffChunk chunk : chunks) {
                if (!aiSkillRuleMatcher.matchesChunk(skill, chunk)) {
                    continue;
                }
                result.aiCallCount++;
                try {
                    aiReviewExecutor.execute(taskId, project, rule, skill, chunk, branch, reviewDays);
                } catch (Exception exception) {
                    result.warnings.add(limit("AI review skipped for rule " + sourceName(rule.getRuleName(), rule.getId())
                        + ", skill " + sourceName(skill.getSkillName(), skill.getId())
                        + ", file " + chunk.getFilePath()
                        + ", chunk " + chunk.getChunkIndex()
                        + ": " + exception.getMessage(), 500));
                }
            }
        }
        return result;
    }

    private TaskIssueCounters countIssues(Long taskId) {
        TaskIssueCounters counters = new TaskIssueCounters();
        counters.issueCount = reviewIssueMapper.selectCount(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getTaskId, taskId)).intValue();
        counters.blockerCount = countSeverity(taskId, "BLOCKER");
        counters.criticalCount = countSeverity(taskId, "CRITICAL");
        counters.majorCount = countSeverity(taskId, "MAJOR");
        counters.minorCount = countSeverity(taskId, "MINOR");
        counters.infoCount = countSeverity(taskId, "INFO");
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
                             AiRuleExecutionResult aiRuleResult,
                             ScriptRuleExecutionResult scriptRuleResult) {
        LambdaUpdateWrapper<ReviewTaskEntity> wrapper = new LambdaUpdateWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getId, taskId)
            .set(ReviewTaskEntity::getStatus, ReviewTaskStatus.SUCCESS.name())
            .set(ReviewTaskEntity::getCommitCount, diffSummary.getCommitCount())
            .set(ReviewTaskEntity::getDiffFileCount, diffSummary.getDiffFileCount())
            .set(ReviewTaskEntity::getIssueCount, counters.issueCount)
            .set(ReviewTaskEntity::getBlockerCount, counters.blockerCount)
            .set(ReviewTaskEntity::getCriticalCount, counters.criticalCount)
            .set(ReviewTaskEntity::getMajorCount, counters.majorCount)
            .set(ReviewTaskEntity::getMinorCount, counters.minorCount)
            .set(ReviewTaskEntity::getInfoCount, counters.infoCount)
            .set(ReviewTaskEntity::getAiCallCount, aiRuleResult.aiCallCount)
            .set(ReviewTaskEntity::getSkippedCommitCount, diffSummary.getSkippedCommitCount())
            .set(ReviewTaskEntity::getSkippedFileCount, diffSummary.getSkippedFileCount())
            .set(ReviewTaskEntity::getEndTime, LocalDateTime.now())
            .set(ReviewTaskEntity::getWarningMessage, warningMessage(diffSummary, aiRuleResult.warnings, scriptRuleResult.getWarnings()))
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

    private String sourceName(String name, Long id) {
        return StringUtils.hasText(name) ? name + " (#" + id + ")" : "#" + id;
    }

    private String warningMessage(GitDiffSummary diffSummary, List<String> aiWarnings, List<String> scriptWarnings) {
        List<String> warnings = new ArrayList<>();
        if (diffSummary.getWarnings() != null && !diffSummary.getWarnings().isEmpty()) {
            warnings.addAll(diffSummary.getWarnings());
        }
        if (aiWarnings != null && !aiWarnings.isEmpty()) {
            warnings.addAll(aiWarnings);
        }
        if (scriptWarnings != null && !scriptWarnings.isEmpty()) {
            warnings.addAll(scriptWarnings);
        }
        if (warnings.isEmpty()) {
            return null;
        }
        return limit(String.join(" ", warnings), 1000);
    }

    private static class AiRuleExecutionResult {
        private Integer aiCallCount = 0;
        private List<String> warnings = new ArrayList<>();
    }

    private static class TaskIssueCounters {
        private Integer issueCount = 0;
        private Integer blockerCount = 0;
        private Integer criticalCount = 0;
        private Integer majorCount = 0;
        private Integer minorCount = 0;
        private Integer infoCount = 0;
    }
}
