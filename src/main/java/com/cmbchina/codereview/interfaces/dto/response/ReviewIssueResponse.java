package com.cmbchina.codereview.interfaces.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReviewIssueResponse {

    private Long id;

    private Long taskId;

    private String taskNo;

    private Long projectId;

    private String assigneeUserId;

    private String assigneeName;

    private String assigneeEmployeeId;

    private String commitAuthor;

    private Long ruleId;

    private Long skillId;

    private Long scriptId;

    private String ruleName;

    private String skillName;

    private String scriptName;

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

    private String codeDetailUrl;

    private String rawResponse;

    private String status;

    private LocalDateTime createTime;
}
