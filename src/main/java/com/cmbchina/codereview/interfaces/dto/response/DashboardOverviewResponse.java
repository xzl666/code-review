package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class DashboardOverviewResponse {

    private Long projectCount;

    private Long enabledProjectCount;

    private Long todayTaskCount;

    private Long todayIssueCount;

    private Long openIssueCount;

    private Long todayAiCallCount;

    private Long totalTokenCount;

    private Long todayTokenCount;

    private Long criticalCount;

    private Long highCount;
}
