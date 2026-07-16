package com.cmbchina.codereview.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ProjectMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewRuleMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewTaskMapper;
import com.cmbchina.codereview.interfaces.dto.response.DashboardOverviewResponse;
import com.cmbchina.codereview.interfaces.dto.response.NameValueResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardAppServiceTest {

    private final ProjectMapper projectMapper = mock(ProjectMapper.class);

    private final ReviewTaskMapper reviewTaskMapper = mock(ReviewTaskMapper.class);

    private final ReviewIssueMapper reviewIssueMapper = mock(ReviewIssueMapper.class);

    private final ReviewRuleMapper reviewRuleMapper = mock(ReviewRuleMapper.class);

    private final DashboardAppService dashboardAppService = new DashboardAppService(
        projectMapper,
        reviewTaskMapper,
        reviewIssueMapper,
        reviewRuleMapper
    );

    @Test
    void overviewCountsOpenIssuesAndTodayAiCalls() {
        when(projectMapper.selectCount(any(Wrapper.class))).thenReturn(3L, 2L);
        when(reviewTaskMapper.selectCount(any(Wrapper.class))).thenReturn(4L);
        ReviewTaskEntity firstTask = new ReviewTaskEntity();
        firstTask.setAiSuccessCount(4);
        firstTask.setAiFailureCount(1);
        firstTask.setTotalTokenCount(1000L);
        ReviewTaskEntity secondTask = new ReviewTaskEntity();
        secondTask.setAiCallCount(null);
        ReviewTaskEntity thirdTask = new ReviewTaskEntity();
        thirdTask.setAiSuccessCount(7);
        thirdTask.setAiFailureCount(0);
        thirdTask.setTotalTokenCount(2500L);
        when(reviewTaskMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(firstTask, secondTask, thirdTask));
        when(reviewIssueMapper.selectCount(any(Wrapper.class))).thenReturn(8L, 6L, 1L, 2L);

        DashboardOverviewResponse overview = dashboardAppService.overview();

        assertEquals(3L, overview.getProjectCount());
        assertEquals(2L, overview.getEnabledProjectCount());
        assertEquals(4L, overview.getTodayTaskCount());
        assertEquals(8L, overview.getTodayIssueCount());
        assertEquals(6L, overview.getOpenIssueCount());
        assertEquals(12L, overview.getTodayAiCallCount());
        assertEquals(3500L, overview.getTodayTokenCount());
        assertEquals(3500L, overview.getTotalTokenCount());
        assertEquals(1L, overview.getCriticalCount());
        assertEquals(2L, overview.getHighCount());
    }

    @Test
    void severityDistributionReturnsChineseLabelsInFixedOrder() {
        when(reviewIssueMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(
            issue("HIGH"),
            issue("HIGH"),
            issue("MEDIUM"),
            issue("LOW")
        ));

        List<NameValueResponse> distribution = dashboardAppService.severityDistribution();

        assertEquals("严重", distribution.get(0).getName());
        assertEquals(0L, distribution.get(0).getValue());
        assertEquals("高", distribution.get(1).getName());
        assertEquals(2L, distribution.get(1).getValue());
        assertEquals("中", distribution.get(2).getName());
        assertEquals(1L, distribution.get(2).getValue());
        assertEquals("低", distribution.get(3).getName());
        assertEquals(1L, distribution.get(3).getValue());
    }

    @Test
    void issueTrendLimitsRangeToThirtyDays() {
        when(reviewIssueMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        List<NameValueResponse> trend = dashboardAppService.issueTrend(60);

        assertEquals(30, trend.size());
    }

    @Test
    void issueTrendDefaultsToSevenDays() {
        when(reviewIssueMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        List<NameValueResponse> trend = dashboardAppService.issueTrend(null);

        assertEquals(7, trend.size());
    }

    private ReviewIssueEntity issue(String severity) {
        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setSeverity(severity);
        return issue;
    }
}
