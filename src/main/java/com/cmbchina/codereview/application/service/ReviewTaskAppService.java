package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.enums.ReviewTaskStatus;
import com.cmbchina.codereview.common.enums.TriggerType;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
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
        ReviewTaskEntity entity = new ReviewTaskEntity();
        entity.setTaskNo(generateTaskNo());
        entity.setProjectId(project.getId());
        entity.setProjectName(project.getProjectName());
        entity.setTriggerType(triggerType);
        entity.setReviewBranch(defaultIfBlank(request.getBranch(), project.getDefaultBranch()));
        entity.setReviewDays(request.getReviewDays() == null ? project.getReviewDays() : request.getReviewDays());
        entity.setCommitCount(0);
        entity.setDiffFileCount(0);
        entity.setIssueCount(0);
        entity.setBlockerCount(0);
        entity.setCriticalCount(0);
        entity.setMajorCount(0);
        entity.setMinorCount(0);
        entity.setInfoCount(0);
        entity.setAiCallCount(0);
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
        response.setReviewDays(entity.getReviewDays());
        response.setCommitCount(entity.getCommitCount());
        response.setDiffFileCount(entity.getDiffFileCount());
        response.setIssueCount(entity.getIssueCount());
        response.setBlockerCount(entity.getBlockerCount());
        response.setCriticalCount(entity.getCriticalCount());
        response.setMajorCount(entity.getMajorCount());
        response.setMinorCount(entity.getMinorCount());
        response.setInfoCount(entity.getInfoCount());
        response.setAiCallCount(entity.getAiCallCount());
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
