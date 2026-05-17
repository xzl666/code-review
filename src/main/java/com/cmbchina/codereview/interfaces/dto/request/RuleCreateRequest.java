package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RuleCreateRequest {

    @NotBlank(message = "不能为空")
    private String ruleName;

    @NotBlank(message = "不能为空")
    private String ruleCode;

    @NotBlank(message = "不能为空")
    private String ruleKind;

    @NotBlank(message = "不能为空")
    private String ruleType;

    @NotBlank(message = "不能为空")
    private String severity;

    private String projectType = "ALL";

    private String promptTemplate;

    private Long skillId;

    private Long scriptId;

    private Integer sortOrder = 0;
}
