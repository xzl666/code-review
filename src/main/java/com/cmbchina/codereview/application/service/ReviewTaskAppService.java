package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.enums.ReviewTaskStatus;
import com.cmbchina.codereview.common.enums.TriggerType;
import com.cmbchina.codereview.common.enums.OcrReviewMode;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewTaskMapper;
import com.cmbchina.codereview.interfaces.dto.request.ManualReviewStartRequest;
import com.cmbchina.codereview.interfaces.dto.request.ReviewTaskPageRequest;
import com.cmbchina.codereview.interfaces.dto.response.ReviewTaskResponse;
import com.cmbchina.codereview.interfaces.dto.response.ReviewTaskStatisticsResponse;
import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@DependsOn("schemaMigrationService")
public class ReviewTaskAppService {

    private final ReviewTaskMapper reviewTaskMapper;

    private final ProjectRepository projectRepository;

    private final ReviewEngineAppService reviewEngineAppService;

    public ReviewTaskAppService(ReviewTaskMapper reviewTaskMapper,
                                ProjectRepository projectRepository,
                                ReviewEngineAppService reviewEngineAppService) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.projectRepository = projectRepository;
        this.reviewEngineAppService = reviewEngineAppService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReviewTaskResponse manualStart(ManualReviewStartRequest request) {
        return start(request, TriggerType.MANUAL.name());
    }

    @Transactional(rollbackFor = Exception.class)
    public ReviewTaskResponse scheduledStart(ManualReviewStartRequest request) {
        return start(request, TriggerType.SCHEDULE.name());
    }

    private ReviewTaskResponse start(ManualReviewStartRequest request, String triggerType) {
        Project project = projectRepository.findById(request.getProjectId());
        if (project == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        if (TriggerType.MANUAL.name().equals(triggerType) && hasActiveTask(project.getId())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "该项目已有待执行或执行中的检视任务，请等待完成后再触发");
        }
        ReviewTaskEntity entity = new ReviewTaskEntity();
        entity.setTaskNo(generateTaskNo());
        entity.setProjectId(project.getId());
        entity.setProjectName(project.getProjectName());
        entity.setTriggerType(triggerType);
        entity.setReviewBranch(defaultIfBlank(request.getBranch(), project.getDefaultBranch()));
        entity.setReviewDays(0);
        OcrReviewMode reviewMode = reviewMode(request.getReviewMode());
        if (reviewMode == OcrReviewMode.YESTERDAY) {
            LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
            request.setReviewStartTime(yesterday.atStartOfDay());
            request.setReviewEndTime(yesterday.plusDays(1).atStartOfDay());
        }
        validateModeArguments(reviewMode, request);
        entity.setReviewMode(reviewMode.name());
        entity.setBaseRef(trim(request.getBaseRef()));
        entity.setTargetRef(defaultIfBlank(request.getTargetRef(), entity.getReviewBranch()));
        entity.setCommitRef(trim(request.getCommitRef()));
        entity.setScanPath(trim(request.getScanPath()));
        entity.setScanExclude(trim(request.getScanExclude()));
        entity.setScanNoPlan(Boolean.TRUE.equals(request.getScanNoPlan()) ? 1 : 0);
        entity.setMaxTokensBudget(request.getMaxTokensBudget() == null ? 500000L : request.getMaxTokensBudget());
        entity.setReviewBackground(trim(request.getBackground()));
        entity.setReviewStartTime(request.getReviewStartTime());
        entity.setReviewEndTime(request.getReviewEndTime());
        entity.setNotifyEnabled(TriggerType.SCHEDULE.name().equals(triggerType)
            || Boolean.TRUE.equals(request.getSendNotification()) ? 1 : 0);
        entity.setCommitCount(0);
        entity.setDiffFileCount(0);
        entity.setIssueCount(0);
        entity.setCriticalCount(0);
        entity.setHighCount(0);
        entity.setMediumCount(0);
        entity.setLowCount(0);
        entity.setAiCallCount(0);
        entity.setAiSuccessCount(0);
        entity.setAiFailureCount(0);
        entity.setInputTokenCount(0L);
        entity.setOutputTokenCount(0L);
        entity.setTotalTokenCount(0L);
        entity.setCacheReadTokenCount(0L);
        entity.setCacheWriteTokenCount(0L);
        entity.setSkippedCommitCount(0);
        entity.setSkippedFileCount(0);
        entity.setStatus(ReviewTaskStatus.PENDING.name());
        reviewTaskMapper.insert(entity);
        submitAfterCommit(entity.getId());
        return toResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ReviewTaskEntity entity = ensureExists(id);
        if (!ReviewTaskStatus.PENDING.name().equals(entity.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "仅待执行任务可取消");
        }
        updateStatus(id, ReviewTaskStatus.CANCELED.name(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void retry(Long id) {
        ReviewTaskEntity entity = ensureExists(id);
        if (!ReviewTaskStatus.FAILED.name().equals(entity.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "仅失败任务可重试");
        }
        updateStatus(id, ReviewTaskStatus.PENDING.name(), null);
        submitAfterCommit(id);
    }

    public ReviewTaskResponse detail(Long id) {
        return toResponse(ensureExists(id));
    }

    public PageResponse<ReviewTaskResponse> page(ReviewTaskPageRequest request) {
        long pageNo = request.getPageNo() == null ? 1L : request.getPageNo();
        long pageSize = request.getPageSize() == null ? 10L : request.getPageSize();
        LambdaQueryWrapper<ReviewTaskEntity> wrapper = new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(request.getProjectId() != null, ReviewTaskEntity::getProjectId, request.getProjectId())
            .like(StringUtils.hasText(request.getProjectName()), ReviewTaskEntity::getProjectName, request.getProjectName())
            .eq(StringUtils.hasText(request.getStatus()), ReviewTaskEntity::getStatus, request.getStatus())
            .eq(StringUtils.hasText(request.getTriggerType()), ReviewTaskEntity::getTriggerType, request.getTriggerType())
            .orderByDesc(ReviewTaskEntity::getCreateTime);
        Page<ReviewTaskEntity> page = reviewTaskMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ReviewTaskResponse> records = page.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), pageNo, pageSize);
    }

    public ReviewTaskStatisticsResponse statistics() {
        ReviewTaskStatisticsResponse response = new ReviewTaskStatisticsResponse();
        response.setTotalTasks(count(null));
        response.setPendingTasks(count(ReviewTaskStatus.PENDING.name()));
        response.setRunningTasks(count(ReviewTaskStatus.RUNNING.name()));
        response.setSuccessTasks(count(ReviewTaskStatus.SUCCESS.name()));
        response.setFailedTasks(count(ReviewTaskStatus.FAILED.name()));
        return response;
    }

    @PostConstruct
    public void recoverStaleRunningTasks() {
        LocalDateTime timeoutBefore = LocalDateTime.now().minusHours(2);
        LambdaUpdateWrapper<ReviewTaskEntity> wrapper = new LambdaUpdateWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getStatus, ReviewTaskStatus.RUNNING.name())
            .lt(ReviewTaskEntity::getStartTime, timeoutBefore)
            .set(ReviewTaskEntity::getStatus, ReviewTaskStatus.FAILED.name())
            .set(ReviewTaskEntity::getEndTime, LocalDateTime.now())
            .set(ReviewTaskEntity::getWarningMessage, null)
            .set(ReviewTaskEntity::getErrorMessage, "任务运行超时，系统启动时自动关闭");
        reviewTaskMapper.update(null, wrapper);
    }

    private Long count(String status) {
        LambdaQueryWrapper<ReviewTaskEntity> wrapper = new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(StringUtils.hasText(status), ReviewTaskEntity::getStatus, status);
        return reviewTaskMapper.selectCount(wrapper);
    }

    private boolean hasActiveTask(Long projectId) {
        Long count = reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getProjectId, projectId)
            .in(ReviewTaskEntity::getStatus, ReviewTaskStatus.PENDING.name(), ReviewTaskStatus.RUNNING.name()));
        return count != null && count > 0;
    }

    private void updateStatus(Long id, String status, String errorMessage) {
        LambdaUpdateWrapper<ReviewTaskEntity> wrapper = new LambdaUpdateWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getId, id)
            .set(ReviewTaskEntity::getStatus, status)
            .set(ReviewTaskEntity::getWarningMessage, null)
            .set(ReviewTaskEntity::getErrorMessage, errorMessage);
        reviewTaskMapper.update(null, wrapper);
    }

    private ReviewTaskEntity ensureExists(Long id) {
        ReviewTaskEntity entity = reviewTaskMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "检视任务不存在");
        }
        return entity;
    }

    private ReviewTaskResponse toResponse(ReviewTaskEntity entity) {
        ReviewTaskResponse response = new ReviewTaskResponse();
        response.setId(entity.getId());
        response.setTaskNo(entity.getTaskNo());
        response.setProjectId(entity.getProjectId());
        response.setProjectName(entity.getProjectName());
        response.setTriggerType(entity.getTriggerType());
        response.setReviewBranch(entity.getReviewBranch());
        response.setReviewMode(entity.getReviewMode());
        response.setBaseRef(entity.getBaseRef());
        response.setTargetRef(entity.getTargetRef());
        response.setCommitRef(entity.getCommitRef());
        response.setScanPath(entity.getScanPath());
        response.setScanExclude(entity.getScanExclude());
        response.setScanNoPlan(entity.getScanNoPlan());
        response.setMaxTokensBudget(entity.getMaxTokensBudget());
        response.setReviewBackground(entity.getReviewBackground());
        response.setReviewStartTime(entity.getReviewStartTime());
        response.setReviewEndTime(entity.getReviewEndTime());
        response.setNotifyEnabled(entity.getNotifyEnabled());
        response.setCommitCount(entity.getCommitCount());
        response.setDiffFileCount(entity.getDiffFileCount());
        response.setIssueCount(entity.getIssueCount());
        response.setCriticalCount(entity.getCriticalCount());
        response.setHighCount(entity.getHighCount());
        response.setMediumCount(entity.getMediumCount());
        response.setLowCount(entity.getLowCount());
        response.setAiCallCount(value(entity.getAiSuccessCount()) + value(entity.getAiFailureCount()));
        response.setAiSuccessCount(entity.getAiSuccessCount());
        response.setAiFailureCount(entity.getAiFailureCount());
        response.setInputTokenCount(entity.getInputTokenCount());
        response.setOutputTokenCount(entity.getOutputTokenCount());
        response.setTotalTokenCount(entity.getTotalTokenCount());
        response.setCacheReadTokenCount(entity.getCacheReadTokenCount());
        response.setCacheWriteTokenCount(entity.getCacheWriteTokenCount());
        response.setSkippedCommitCount(entity.getSkippedCommitCount());
        response.setSkippedFileCount(entity.getSkippedFileCount());
        response.setStatus(entity.getStatus());
        response.setStartTime(entity.getStartTime());
        response.setEndTime(entity.getEndTime());
        response.setWarningMessage(entity.getWarningMessage());
        response.setErrorMessage(entity.getErrorMessage());
        return response;
    }

    private String generateTaskNo() {
        return "CR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private int value(Integer count) {
        return count == null ? 0 : count;
    }

    private OcrReviewMode reviewMode(String value) {
        try {
            return StringUtils.hasText(value) ? OcrReviewMode.valueOf(value.trim().toUpperCase()) : OcrReviewMode.RANGE;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的检视方式");
        }
    }

    private void validateModeArguments(OcrReviewMode mode, ManualReviewStartRequest request) {
        if (mode == OcrReviewMode.COMMIT && !StringUtils.hasText(request.getCommitRef())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单提交检视必须填写提交 SHA 或标签");
        }
        boolean scheduledTimeRange = request.getReviewStartTime() != null && request.getReviewEndTime() != null;
        if (mode == OcrReviewMode.RANGE && !scheduledTimeRange && (!StringUtils.hasText(request.getBaseRef())
            || !StringUtils.hasText(request.getTargetRef()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分支区间检视必须填写起始引用和目标引用");
        }
        if (scheduledTimeRange && !request.getReviewStartTime().isBefore(request.getReviewEndTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检视开始时间必须早于结束时间");
        }
        if (request.getMaxTokensBudget() != null && request.getMaxTokensBudget() < 0L) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Token 预算不能小于 0");
        }
        if (request.getMaxTokensBudget() != null && request.getMaxTokensBudget() > Integer.MAX_VALUE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Token 预算不能大于 " + Integer.MAX_VALUE);
        }
        if (StringUtils.hasText(request.getBackground()) && request.getBackground().length() > 8000) {
            throw new BizException(ErrorCode.PARAM_ERROR, "业务背景不能超过 8000 个字符");
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void submitAfterCommit(Long taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            reviewEngineAppService.submit(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reviewEngineAppService.submit(taskId);
            }
        });
    }
}
