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

    private String reviewMode;

    private String baseRef;

    private String targetRef;

    private String commitRef;

    private String scanPath;

    private String scanExclude;

    private Integer scanNoPlan;

    private Long maxTokensBudget;

    private String reviewBackground;

    private LocalDateTime reviewStartTime;

    private LocalDateTime reviewEndTime;

    private Integer notifyEnabled;

    private Integer commitCount;

    private Integer diffFileCount;

    private Integer issueCount;

    private Integer criticalCount;

    private Integer highCount;

    private Integer mediumCount;

    private Integer lowCount;

    private Integer aiCallCount;

    private Integer aiSuccessCount;

    private Integer aiFailureCount;

    private Long inputTokenCount;

    private Long outputTokenCount;

    private Long totalTokenCount;

    private Long cacheReadTokenCount;

    private Long cacheWriteTokenCount;

    private Integer skippedCommitCount;

    private Integer skippedFileCount;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String warningMessage;

    private String errorMessage;
}
