package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RuleCreateRequest {

    @NotBlank(message = "不能为空")
    private String ruleName;

    @NotBlank(message = "不能为空")
    private String promptTemplate;

    @NotBlank(message = "不能为空")
    private String pathPattern = "**/*";

    private Integer mergeSystemRule = 1;

    private Integer sortOrder = 0;
}
