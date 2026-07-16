package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class ReviewIssueStatisticsResponse {

    private Long totalIssues;

    private Long openIssues;

    private Long ignoredIssues;

    private Long fixedIssues;

    private Long criticalCount;

    private Long highCount;

    private Long mediumCount;

    private Long lowCount;
}
