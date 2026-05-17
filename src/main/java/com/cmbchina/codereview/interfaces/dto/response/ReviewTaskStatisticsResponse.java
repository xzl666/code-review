package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class ReviewTaskStatisticsResponse {

    private Long totalTasks;

    private Long pendingTasks;

    private Long runningTasks;

    private Long successTasks;

    private Long failedTasks;
}
