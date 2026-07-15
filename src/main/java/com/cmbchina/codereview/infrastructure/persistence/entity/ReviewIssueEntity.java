package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_review_issue")
public class ReviewIssueEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long projectId;

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

    private String rawResponse;

    private String status;
}
