package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScriptUpdateRequest {

    @NotNull(message = "不能为空")
    private Long id;

    @NotBlank(message = "不能为空")
    private String scriptName;

    @NotBlank(message = "不能为空")
    private String scriptCode;

    @NotBlank(message = "不能为空")
    private String scriptLanguage;

    @NotBlank(message = "不能为空")
    private String scriptContent;

    private String parameterTemplate;

    @Min(value = 1, message = "必须大于 0")
    private Integer timeoutSeconds;

    private Integer generatedByAi;

    private Integer status;
}
