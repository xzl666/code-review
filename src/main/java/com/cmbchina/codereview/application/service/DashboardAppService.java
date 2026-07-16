package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cmbchina.codereview.infrastructure.persistence.entity.ProjectEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ProjectMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewRuleMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewTaskMapper;
import com.cmbchina.codereview.interfaces.dto.response.DashboardOverviewResponse;
import com.cmbchina.codereview.interfaces.dto.response.NameValueResponse;
import com.cmbchina.codereview.common.context.CurrentUserContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DashboardAppService {

    private final ProjectMapper projectMapper;

    private final ReviewTaskMapper reviewTaskMapper;

    private final ReviewIssueMapper reviewIssueMapper;

    private final ReviewRuleMapper reviewRuleMapper;

    public DashboardAppService(ProjectMapper projectMapper,
                               ReviewTaskMapper reviewTaskMapper,
                               ReviewIssueMapper reviewIssueMapper,
                               ReviewRuleMapper reviewRuleMapper) {
        this.projectMapper = projectMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewIssueMapper = reviewIssueMapper;
        this.reviewRuleMapper = reviewRuleMapper;
    }

    public DashboardOverviewResponse overview() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        DashboardOverviewResponse response = new DashboardOverviewResponse();
        response.setProjectCount(projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>()));
        response.setEnabledProjectCount(projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>().eq(ProjectEntity::getStatus, 1)));
        response.setTodayTaskCount(reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTaskEntity>().ge(ReviewTaskEntity::getCreateTime, today)));
        response.setTodayIssueCount(reviewIssueMapper.selectCount(visibleIssues().ge(ReviewIssueEntity::getCreateTime, today)));
        response.setOpenIssueCount(reviewIssueMapper.selectCount(visibleIssues().eq(ReviewIssueEntity::getStatus, "OPEN")));
        List<ReviewTaskEntity> todayTasks = reviewTaskMapper.selectList(
            new LambdaQueryWrapper<ReviewTaskEntity>().ge(ReviewTaskEntity::getCreateTime, today));
        List<ReviewTaskEntity> allTasks = reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTaskEntity>());
        response.setTodayAiCallCount(todayTasks.stream()
            .mapToLong(task -> value(task.getAiSuccessCount()) + value(task.getAiFailureCount()))
            .sum());
        response.setTodayTokenCount(todayTasks.stream()
            .mapToLong(task -> task.getTotalTokenCount() == null ? 0L : task.getTotalTokenCount())
            .sum());
        response.setTotalTokenCount(allTasks.stream()
            .mapToLong(task -> task.getTotalTokenCount() == null ? 0L : task.getTotalTokenCount())
            .sum());
        response.setCriticalCount(reviewIssueMapper.selectCount(visibleIssues().eq(ReviewIssueEntity::getSeverity, "CRITICAL")));
        response.setHighCount(reviewIssueMapper.selectCount(visibleIssues().eq(ReviewIssueEntity::getSeverity, "HIGH")));
        return response;
    }

    private long value(Integer count) {
        return count == null ? 0L : count;
    }

    public List<NameValueResponse> severityDistribution() {
        List<ReviewIssueEntity> issues = reviewIssueMapper.selectList(visibleIssues());
        Map<String, Long> grouped = issues.stream().collect(Collectors.groupingBy(ReviewIssueEntity::getSeverity, Collectors.counting()));
        return Arrays.asList(
            new NameValueResponse("\u4e25\u91cd", grouped.getOrDefault("CRITICAL", 0L)),
            new NameValueResponse("\u9ad8", grouped.getOrDefault("HIGH", 0L)),
            new NameValueResponse("\u4e2d", grouped.getOrDefault("MEDIUM", 0L)),
            new NameValueResponse("\u4f4e", grouped.getOrDefault("LOW", 0L))
        );
    }

    public List<NameValueResponse> issueTrend(Integer days) {
        int rangeDays = days == null || days <= 0 ? 7 : Math.min(days, 30);
        return java.util.stream.IntStream.rangeClosed(0, rangeDays - 1)
            .mapToObj(offset -> {
                LocalDate day = LocalDate.now().minusDays((long) rangeDays - 1 - offset);
                Long count = reviewIssueMapper.selectCount(visibleIssues()
                    .ge(ReviewIssueEntity::getCreateTime, day.atStartOfDay())
                    .lt(ReviewIssueEntity::getCreateTime, day.plusDays(1).atStartOfDay()));
                return new NameValueResponse(day.toString(), count);
            })
            .collect(Collectors.toList());
    }

    public List<NameValueResponse> projectRanking() {
        List<ReviewIssueEntity> issues = reviewIssueMapper.selectList(visibleIssues());
        return issues.stream()
            .collect(Collectors.groupingBy(issue -> String.valueOf(issue.getProjectId()), Collectors.counting()))
            .entrySet().stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(10)
            .map(entry -> new NameValueResponse(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    public List<NameValueResponse> ruleHitRanking() {
        List<ReviewIssueEntity> issues = reviewIssueMapper.selectList(visibleIssues());
        Map<Long, String> ruleNames = reviewRuleMapper.selectList(new LambdaQueryWrapper<ReviewRuleEntity>()).stream()
            .collect(Collectors.toMap(ReviewRuleEntity::getId, ReviewRuleEntity::getRuleName, (a, b) -> a));
        return issues.stream()
            .filter(issue -> issue.getRuleId() != null)
            .collect(Collectors.groupingBy(ReviewIssueEntity::getRuleId, Collectors.counting()))
            .entrySet().stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(10)
            .map(entry -> new NameValueResponse(ruleNames.getOrDefault(entry.getKey(), String.valueOf(entry.getKey())), entry.getValue()))
            .collect(Collectors.toList());
    }

    private LambdaQueryWrapper<ReviewIssueEntity> visibleIssues() {
        LambdaQueryWrapper<ReviewIssueEntity> wrapper = new LambdaQueryWrapper<>();
        String userId = CurrentUserContext.get();
        if (!CurrentUserContext.isAdmin() && StringUtils.hasText(userId) && userId.matches("[A-Fa-f0-9]{32,64}")) {
            wrapper.and(query -> query.eq(ReviewIssueEntity::getAssigneeUserId, userId).or()
                .inSql(ReviewIssueEntity::getProjectId,
                    "SELECT project_id FROM cr_project_owner WHERE user_id = '" + userId + "'"));
        }
        return wrapper;
    }
}
