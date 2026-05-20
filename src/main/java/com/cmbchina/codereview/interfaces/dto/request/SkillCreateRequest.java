package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SkillCreateRequest {

    @NotBlank(message = "不能为空")
    private String skillName;

    @NotBlank(message = "不能为空")
    private String skillCode;

    @NotBlank(message = "不能为空")
    private String functionName;

    private String functionDescription;

    @NotBlank(message = "不能为空")
    private String parametersSchema;

    private String version = "1.0.0";

    private String projectType = "ALL";

    private Integer ruleMatchingEnabled = 0;

    private String matchRules;
}
