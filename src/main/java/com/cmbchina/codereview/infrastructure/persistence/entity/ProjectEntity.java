package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_project")
public class ProjectEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectName;

    private String projectType;

    private String repoUrl;

    private String projectToken;

    private Integer useDefaultToken;

    private String defaultBranch;

    private String ownerName;

    private Integer reviewDays;

    private String scheduleCron;

    private Integer scheduleEnabled;

    private Integer notifyEnabled;

    private Integer status;

    private String remark;
}
