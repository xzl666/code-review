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
            int aiCallCount = executeAiRules(taskId, project, diffSummary, branch, task.getReviewDays());
            executeScriptRules(taskId, project, diffSummary, branch);
            TaskIssueCounters counters = countIssues(taskId);
            markSuccess(taskId, diffSummary, counters, aiCallCount);
        } catch (Exception exception) {
            markFailed(taskId, exception.getMessage());
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

    private void executeScriptRules(Long taskId, Project project, GitDiffSummary diffSummary, String branch) {
        LambdaQueryWrapper<ReviewRuleEntity> wrapper = new LambdaQueryWrapper<ReviewRuleEntity>()
            .eq(ReviewRuleEntity::getStatus, BaseStatus.ENABLED.getValue())
            .eq(ReviewRuleEntity::getRuleKind, RuleKind.SCRIPT.name())
            .and(rule -> rule.eq(ReviewRuleEntity::getProjectType, "ALL")
                .or()
                .eq(ReviewRuleEntity::getProjectType, project.getProjectType()))
            .orderByAsc(ReviewRuleEntity::getSortOrder)
            .orderByAsc(ReviewRuleEntity::getId);
        List<ReviewRuleEntity> rules = reviewRuleMapper.selectList(wrapper);
        for (ReviewRuleEntity rule : rules) {
            if (rule.getScriptId() == null) {
                continue;
            }
            ScriptRuleEntity script = scriptRuleMapper.selectById(rule.getScriptId());
            if (script == null || script.getStatus() == null || script.getStatus() != BaseStatus.ENABLED.getValue()) {
                continue;
            }
            scriptReviewExecutor.execute(taskId, project, rule, script, diffSummary, branch);
        }
    }

    private int executeAiRules(Long taskId, Project project, GitDiffSummary diffSummary, String branch, Integer reviewDays) {
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
            return 0;
        }
        List<DiffChunk> chunks = diffChunkService.split(diffSummary, deepSeekProperties.getMaxDiffCharsPerRequest());
        if (chunks.isEmpty()) {
            return 0;
        }
        int aiCallCount = 0;
        for (ReviewRuleEntity rule : rules) {
            if (rule.getSkillId() == null) {
                continue;
            }
            AiSkillEntity skill = aiSkillMapper.selectById(rule.getSkillId());
            if (skill == null || skill.getStatus() == null || skill.getStatus() != BaseStatus.ENABLED.getValue()) {
                continue;
            }
            for (DiffChunk chunk : chunks) {
                aiReviewExecutor.execute(taskId, project, rule, skill, chunk, branch, reviewDays);
                aiCallCount++;
            }
        }
        return aiCallCount;
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

    private void markSuccess(Long taskId, GitDiffSummary diffSummary, TaskIssueCounters counters, int aiCallCount) {
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
            .set(ReviewTaskEntity::getAiCallCount, aiCallCount)
            .set(ReviewTaskEntity::getEndTime, LocalDateTime.now())
            .set(ReviewTaskEntity::getErrorMessage, null);
        reviewTaskMapper.update(null, wrapper);
    }

    private void markFailed(Long taskId, String message) {
        LambdaUpdateWrapper<ReviewTaskEntity> wrapper = new LambdaUpdateWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getId, taskId)
            .set(ReviewTaskEntity::getStatus, ReviewTaskStatus.FAILED.name())
            .set(ReviewTaskEntity::getEndTime, LocalDateTime.now())
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

    private static class TaskIssueCounters {
        private Integer issueCount = 0;
        private Integer blockerCount = 0;
        private Integer criticalCount = 0;
        private Integer majorCount = 0;
        private Integer minorCount = 0;
        private Integer infoCount = 0;
    }
}
