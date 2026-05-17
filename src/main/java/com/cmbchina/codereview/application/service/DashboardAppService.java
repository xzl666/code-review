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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

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
        response.setTodayIssueCount(reviewIssueMapper.selectCount(new LambdaQueryWrapper<ReviewIssueEntity>().ge(ReviewIssueEntity::getCreateTime, today)));
        response.setBlockerCount(reviewIssueMapper.selectCount(new LambdaQueryWrapper<ReviewIssueEntity>().eq(ReviewIssueEntity::getSeverity, "BLOCKER")));
        response.setCriticalCount(reviewIssueMapper.selectCount(new LambdaQueryWrapper<ReviewIssueEntity>().eq(ReviewIssueEntity::getSeverity, "CRITICAL")));
        return response;
    }

    public List<NameValueResponse> severityDistribution() {
        List<ReviewIssueEntity> issues = reviewIssueMapper.selectList(new LambdaQueryWrapper<ReviewIssueEntity>());
        Map<String, Long> grouped = issues.stream().collect(Collectors.groupingBy(ReviewIssueEntity::getSeverity, Collectors.counting()));
        return Arrays.asList(
            new NameValueResponse("阻断", grouped.getOrDefault("BLOCKER", 0L)),
            new NameValueResponse("严重", grouped.getOrDefault("CRITICAL", 0L)),
            new NameValueResponse("主要", grouped.getOrDefault("MAJOR", 0L)),
            new NameValueResponse("次要", grouped.getOrDefault("MINOR", 0L)),
            new NameValueResponse("提示", grouped.getOrDefault("INFO", 0L))
        );
    }

    public List<NameValueResponse> issueTrend() {
        return java.util.stream.IntStream.rangeClosed(0, 6)
            .mapToObj(offset -> {
                LocalDate day = LocalDate.now().minusDays(6L - offset);
                Long count = reviewIssueMapper.selectCount(new LambdaQueryWrapper<ReviewIssueEntity>()
                    .ge(ReviewIssueEntity::getCreateTime, day.atStartOfDay())
                    .lt(ReviewIssueEntity::getCreateTime, day.plusDays(1).atStartOfDay()));
                return new NameValueResponse(day.toString(), count);
            })
            .collect(Collectors.toList());
    }

    public List<NameValueResponse> projectRanking() {
        List<ReviewIssueEntity> issues = reviewIssueMapper.selectList(new LambdaQueryWrapper<ReviewIssueEntity>());
        return issues.stream()
            .collect(Collectors.groupingBy(issue -> String.valueOf(issue.getProjectId()), Collectors.counting()))
            .entrySet().stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(10)
            .map(entry -> new NameValueResponse(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    public List<NameValueResponse> ruleHitRanking() {
        List<ReviewIssueEntity> issues = reviewIssueMapper.selectList(new LambdaQueryWrapper<ReviewIssueEntity>());
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
}
