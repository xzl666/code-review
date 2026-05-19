package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewIssuePageRequest extends PageRequest {

    private Long taskId;

    private String taskNo;

    private Long projectId;

    private String severity;

    private String issueSource;

    private String status;
}
