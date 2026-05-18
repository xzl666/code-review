package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.Min;
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
    private String projectCode;

    @NotBlank(message = "不能为空")
    private String projectType;

    @NotBlank(message = "不能为空")
    private String repoUrl;

    private String projectToken;

    private Integer useDefaultToken;

    private String defaultBranch;

    private String ownerName;

    @Min(value = 1, message = "必须大于 0")
    private Integer reviewDays;

    private String scheduleCron;

    private Integer scheduleEnabled;

    private Integer status;

    private String remark;
}
