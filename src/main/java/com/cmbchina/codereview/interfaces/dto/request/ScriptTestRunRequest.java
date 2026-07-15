package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScriptTestRunRequest {

    private Long scriptId;

    @NotBlank(message = "不能为空")
    private String scriptContent;

    private String inputJson;

    @Min(value = 1, message = "必须大于 0")
    private Integer timeoutSeconds = 10;
}
