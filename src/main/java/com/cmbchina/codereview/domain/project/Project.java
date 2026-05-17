package com.cmbchina.codereview.domain.project;

import lombok.Data;

@Data
public class Project {

    private Long id;

    private String projectName;

    private String projectCode;

    private String projectType;

    private String repoUrl;

    private String projectToken;

    private Integer useDefaultToken;

    private String defaultBranch;

    private String ownerName;

    private Integer reviewDays;

    private Integer status;

    private String remark;
}
