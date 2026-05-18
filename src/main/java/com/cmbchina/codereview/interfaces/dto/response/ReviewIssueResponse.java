package com.cmbchina.codereview.interfaces.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReviewIssueResponse {

    private Long id;

    private Long taskId;

    private Long projectId;

    private Long ruleId;

    private Long skillId;

    private String issueSource;

    private String severity;

    private String issueType;

    private String filePath;

    private Integer startLine;

    private Integer endLine;

    private String summary;

    private String detail;

    private String suggestion;

    private String codeSnippet;

    private String rawResponse;

    private String status;

    private LocalDateTime createTime;
}
