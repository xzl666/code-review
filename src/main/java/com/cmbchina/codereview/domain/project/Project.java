package com.cmbchina.codereview.domain.project;

import java.util.List;
import lombok.Data;

@Data
public class Project {

    private Long id;

    private String projectName;

    private String projectType;

    private String repoUrl;

    private String projectToken;

    private Integer useDefaultToken;

    private String defaultBranch;

    private String ownerName;

    private List<String> ownerUserIds;

    private Integer reviewDays;

    private String scheduleCron;

    private Integer scheduleEnabled;

    private Integer notifyEnabled;

    private Integer status;

    private String remark;
}
