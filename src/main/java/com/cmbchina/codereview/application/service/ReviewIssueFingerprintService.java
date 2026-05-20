package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cmbchina.codereview.common.enums.ReviewIssueStatus;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewIssueFingerprintService {

    private final ReviewIssueMapper reviewIssueMapper;

    public ReviewIssueFingerprintService(ReviewIssueMapper reviewIssueMapper) {
        this.reviewIssueMapper = reviewIssueMapper;
    }

    public void applyHistoricalIgnoredIssues(Long taskId, Long projectId) {
        List<ReviewIssueEntity> currentIssues = reviewIssueMapper.selectList(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getTaskId, taskId)
            .eq(ReviewIssueEntity::getProjectId, projectId)
            .eq(ReviewIssueEntity::getStatus, ReviewIssueStatus.OPEN.name()));
        if (currentIssues.isEmpty()) {
            return;
        }
        List<ReviewIssueEntity> ignoredIssues = reviewIssueMapper.selectList(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getProjectId, projectId)
            .eq(ReviewIssueEntity::getStatus, ReviewIssueStatus.IGNORED.name())
            .ne(ReviewIssueEntity::getTaskId, taskId));
        if (ignoredIssues.isEmpty()) {
            return;
        }
        Map<String, List<ReviewIssueEntity>> ignoredByFingerprint = ignoredIssues.stream()
            .collect(Collectors.groupingBy(this::fingerprint));
        for (ReviewIssueEntity issue : currentIssues) {
            if (ignoredByFingerprint.containsKey(fingerprint(issue))) {
                reviewIssueMapper.update(null, new LambdaUpdateWrapper<ReviewIssueEntity>()
                    .eq(ReviewIssueEntity::getId, issue.getId())
                    .set(ReviewIssueEntity::getStatus, ReviewIssueStatus.IGNORED.name()));
            }
        }
    }

    public LocalDateTime firstSeenAt(ReviewIssueEntity issue) {
        List<ReviewIssueEntity> issues = reviewIssueMapper.selectList(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getProjectId, issue.getProjectId())
            .eq(ReviewIssueEntity::getFilePath, issue.getFilePath())
            .orderByAsc(ReviewIssueEntity::getCreateTime));
        String fingerprint = fingerprint(issue);
        return issues.stream()
            .filter(candidate -> fingerprint.equals(fingerprint(candidate)))
            .map(ReviewIssueEntity::getCreateTime)
            .filter(time -> time != null)
            .findFirst()
            .orElse(issue.getCreateTime());
    }

    public String fingerprint(ReviewIssueEntity issue) {
        String normalizedSummary = normalize(issue.getSummary());
        String line = issue.getStartLine() == null || issue.getStartLine() < 1 ? "" : String.valueOf(issue.getStartLine());
        return normalize(issue.getFilePath()) + "|"
            + normalize(issue.getIssueType()) + "|"
            + line + "|"
            + normalizedSummary;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
