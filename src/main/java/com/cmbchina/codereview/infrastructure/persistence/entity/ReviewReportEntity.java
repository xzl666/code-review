package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_review_report")
public class ReviewReportEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String taskNo;

    private Long projectId;

    private String reportTitle;

    private String reportContent;

    private Integer activeIssueCount;

    private Integer ignoredIssueCount;
}
