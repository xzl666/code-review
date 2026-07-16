package com.cmbchina.codereview.interfaces.dto.request;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectUpdateRequest {

    @NotNull(message = "不能为空")
    private Long id;

    @NotBlank(message = "不能为空")
    private String projectName;

    @NotBlank(message = "不能为空")
    private String projectType;

    @NotBlank(message = "不能为空")
    private String repoUrl;

    private String projectToken;

    private Integer useDefaultToken;

    private String defaultBranch;

    private String ownerName;

    private List<String> ownerUserIds;

    private String scheduleCron;

    private Integer scheduleEnabled;

    private Integer notifyEnabled;

    private String notifyWebhookUrl;

    private String notifyExtraParams;

    private Integer status;

    private String remark;
}
