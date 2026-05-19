package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.enums.ReviewIssueStatus;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewTaskMapper;
import com.cmbchina.codereview.interfaces.dto.request.ReviewIssuePageRequest;
import com.cmbchina.codereview.interfaces.dto.response.ReviewIssueResponse;
import com.cmbchina.codereview.interfaces.dto.response.ReviewIssueStatisticsResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReviewIssueAppService {

    private final ReviewIssueMapper reviewIssueMapper;

    private final ReviewTaskMapper reviewTaskMapper;

    public ReviewIssueAppService(ReviewIssueMapper reviewIssueMapper,
                                 ReviewTaskMapper reviewTaskMapper) {
        this.reviewIssueMapper = reviewIssueMapper;
        this.reviewTaskMapper = reviewTaskMapper;
    }

    public PageResponse<ReviewIssueResponse> page(ReviewIssuePageRequest request) {
        long pageNo = request.getPageNo() == null ? 1L : request.getPageNo();
        long pageSize = request.getPageSize() == null ? 10L : request.getPageSize();
        LambdaQueryWrapper<ReviewIssueEntity> wrapper = queryWrapper(request)
            .orderByDesc(ReviewIssueEntity::getCreateTime);
        Page<ReviewIssueEntity> page = reviewIssueMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ReviewIssueResponse> records = page.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), pageNo, pageSize);
    }

    public ReviewIssueResponse detail(Long id) {
        return toResponse(ensureExists(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void ignore(Long id) {
        updateStatus(id, ReviewIssueStatus.IGNORED.name());
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFixed(Long id) {
        updateStatus(id, ReviewIssueStatus.FIXED.name());
    }

    public ReviewIssueStatisticsResponse statistics(ReviewIssuePageRequest request) {
        ReviewIssueStatisticsResponse response = new ReviewIssueStatisticsResponse();
        response.setTotalIssues(count(request, null, null));
        response.setOpenIssues(count(request, "status", ReviewIssueStatus.OPEN.name()));
        response.setIgnoredIssues(count(request, "status", ReviewIssueStatus.IGNORED.name()));
        response.setFixedIssues(count(request, "status", ReviewIssueStatus.FIXED.name()));
        response.setBlockerCount(count(request, "severity", "BLOCKER"));
        response.setCriticalCount(count(request, "severity", "CRITICAL"));
        response.setMajorCount(count(request, "severity", "MAJOR"));
        response.setMinorCount(count(request, "severity", "MINOR"));
        response.setInfoCount(count(request, "severity", "INFO"));
        return response;
    }

    public String export(ReviewIssuePageRequest request) {
        List<ReviewIssueResponse> records = page(request).getRecords();
        StringBuilder builder = new StringBuilder();
        builder.append("id,taskId,taskNo,projectId,severity,status,filePath,startLine,endLine,summary\n");
        for (ReviewIssueResponse issue : records) {
            builder.append(issue.getId()).append(',')
                .append(issue.getTaskId()).append(',')
                .append(escape(issue.getTaskNo())).append(',')
                .append(issue.getProjectId()).append(',')
                .append(issue.getSeverity()).append(',')
                .append(issue.getStatus()).append(',')
                .append(escape(issue.getFilePath())).append(',')
                .append(issue.getStartLine()).append(',')
                .append(issue.getEndLine()).append(',')
                .append(escape(issue.getSummary())).append('\n');
        }
        return builder.toString();
    }

    private Long count(ReviewIssuePageRequest request, String field, String value) {
        LambdaQueryWrapper<ReviewIssueEntity> wrapper = queryWrapper(request);
        if ("status".equals(field)) {
            wrapper.eq(ReviewIssueEntity::getStatus, value);
        }
        if ("severity".equals(field)) {
            wrapper.eq(ReviewIssueEntity::getSeverity, value);
        }
        return reviewIssueMapper.selectCount(wrapper);
    }

    private LambdaQueryWrapper<ReviewIssueEntity> queryWrapper(ReviewIssuePageRequest request) {
        return new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(request.getTaskId() != null, ReviewIssueEntity::getTaskId, request.getTaskId())
            .eq(request.getProjectId() != null, ReviewIssueEntity::getProjectId, request.getProjectId())
            .eq(StringUtils.hasText(request.getSeverity()), ReviewIssueEntity::getSeverity, request.getSeverity())
            .eq(StringUtils.hasText(request.getIssueSource()), ReviewIssueEntity::getIssueSource, request.getIssueSource())
            .eq(StringUtils.hasText(request.getStatus()), ReviewIssueEntity::getStatus, request.getStatus());
    }

    private void updateStatus(Long id, String status) {
        ensureExists(id);
        LambdaUpdateWrapper<ReviewIssueEntity> wrapper = new LambdaUpdateWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getId, id)
            .set(ReviewIssueEntity::getStatus, status);
        reviewIssueMapper.update(null, wrapper);
    }

    private ReviewIssueEntity ensureExists(Long id) {
        ReviewIssueEntity entity = reviewIssueMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "问题不存在");
        }
        return entity;
    }

    private ReviewIssueResponse toResponse(ReviewIssueEntity entity) {
        ReviewIssueResponse response = new ReviewIssueResponse();
        response.setId(entity.getId());
        response.setTaskId(entity.getTaskId());
        response.setTaskNo(taskNo(entity.getTaskId()));
        response.setProjectId(entity.getProjectId());
        response.setRuleId(entity.getRuleId());
        response.setSkillId(entity.getSkillId());
        response.setIssueSource(entity.getIssueSource());
        response.setSeverity(entity.getSeverity());
        response.setIssueType(entity.getIssueType());
        response.setFilePath(entity.getFilePath());
        response.setStartLine(entity.getStartLine());
        response.setEndLine(entity.getEndLine());
        response.setSummary(entity.getSummary());
        response.setDetail(entity.getDetail());
        response.setSuggestion(entity.getSuggestion());
        response.setCodeSnippet(entity.getCodeSnippet());
        response.setRawResponse(entity.getRawResponse());
        response.setStatus(entity.getStatus());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

    private String taskNo(Long taskId) {
        if (taskId == null) {
            return null;
        }
        ReviewTaskEntity task = reviewTaskMapper.selectById(taskId);
        return task == null ? null : task.getTaskNo();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
