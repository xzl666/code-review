package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SkillUpdateRequest {

    @NotNull(message = "不能为空")
    private Long id;

    @NotBlank(message = "不能为空")
    private String skillName;

    @NotBlank(message = "不能为空")
    private String skillCode;

    private String version;

    private String projectType;

    private Integer ruleMatchingEnabled;

    private String matchRules;

    @NotBlank(message = "不能为空")
    private String reviewGuidelines;

    private Integer status;
}
