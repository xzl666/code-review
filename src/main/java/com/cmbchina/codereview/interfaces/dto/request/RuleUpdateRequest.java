package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RuleUpdateRequest {

    @NotNull(message = "不能为空")
    private Long id;

    @NotBlank(message = "不能为空")
    private String ruleName;

    @NotBlank(message = "不能为空")
    private String ruleCode;

    @NotBlank(message = "不能为空")
    private String promptTemplate;

    @NotBlank(message = "不能为空")
    private String pathPattern;

    private Integer mergeSystemRule;

    private Integer status;

    private Integer sortOrder;
}
