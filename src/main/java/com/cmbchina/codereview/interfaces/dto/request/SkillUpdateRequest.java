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

    @NotBlank(message = "不能为空")
    private String functionName;

    private String functionDescription;

    @NotBlank(message = "不能为空")
    private String parametersSchema;

    private String version;

    private Integer status;
}
