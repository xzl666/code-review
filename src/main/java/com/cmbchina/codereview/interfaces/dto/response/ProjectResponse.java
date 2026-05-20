package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class ProjectResponse {

    private Long id;

    private String projectName;

    private String projectCode;

    private String projectType;

    private String repoUrl;

    private Integer useDefaultToken;

    private String defaultBranch;

    private String ownerName;

    private Integer reviewDays;

    private String scheduleCron;

    private Integer scheduleEnabled;

    private Integer notifyEnabled;

    private String notifyWebhookUrl;

    private String notifyExtraParams;

    private Integer status;

    private String remark;
}
