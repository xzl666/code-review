package com.cmbchina.codereview.interfaces.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReviewTaskResponse {

    private Long id;

    private String taskNo;

    private Long projectId;

    private String projectName;

    private String triggerType;

    private String reviewBranch;

    private Integer reviewDays;

    private Integer commitCount;

    private Integer diffFileCount;

    private Integer issueCount;

    private Integer blockerCount;

    private Integer criticalCount;

    private Integer majorCount;

    private Integer minorCount;

    private Integer infoCount;

    private Integer aiCallCount;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String errorMessage;
}
