package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_review_task")
public class ReviewTaskEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
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

    private Integer skippedCommitCount;

    private Integer skippedFileCount;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String warningMessage;

    private String errorMessage;
}
