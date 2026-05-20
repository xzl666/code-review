package com.cmbchina.codereview.interfaces.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReviewReportResponse {

    private Long id;

    private Long taskId;

    private String taskNo;

    private Long projectId;

    private String reportTitle;

    private String reportContent;

    private Integer activeIssueCount;

    private Integer ignoredIssueCount;

    private LocalDateTime createTime;
}
