package com.cmbchina.codereview.interfaces.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class ProjectResponse {

    private Long id;

    private String projectName;

    private String projectType;

    private String repoUrl;

    private Integer useDefaultToken;

    private String defaultBranch;

    private String ownerName;

    private List<String> ownerUserIds;

    private List<SystemUserResponse> owners;

    private String scheduleCron;

    private Integer scheduleEnabled;

    private Integer notifyEnabled;

    private Integer status;

    private String remark;
}
