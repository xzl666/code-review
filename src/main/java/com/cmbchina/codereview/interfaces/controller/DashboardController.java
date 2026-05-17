package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.DashboardAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.interfaces.dto.response.DashboardOverviewResponse;
import com.cmbchina.codereview.interfaces.dto.response.NameValueResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardAppService dashboardAppService;

    public DashboardController(DashboardAppService dashboardAppService) {
        this.dashboardAppService = dashboardAppService;
    }

    @PostMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> overview() {
        return ApiResponse.success(dashboardAppService.overview());
    }

    @PostMapping("/issue-trend")
    public ApiResponse<List<NameValueResponse>> issueTrend() {
        return ApiResponse.success(dashboardAppService.issueTrend());
    }

    @PostMapping("/severity-distribution")
    public ApiResponse<List<NameValueResponse>> severityDistribution() {
        return ApiResponse.success(dashboardAppService.severityDistribution());
    }

    @PostMapping("/project-ranking")
    public ApiResponse<List<NameValueResponse>> projectRanking() {
        return ApiResponse.success(dashboardAppService.projectRanking());
    }

    @PostMapping("/rule-hit-ranking")
    public ApiResponse<List<NameValueResponse>> ruleHitRanking() {
        return ApiResponse.success(dashboardAppService.ruleHitRanking());
    }
}
