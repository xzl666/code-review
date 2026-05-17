package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class RuleResponse {

    private Long id;

    private String ruleName;

    private String ruleCode;

    private String ruleKind;

    private String ruleType;

    private String severity;

    private String projectType;

    private String promptTemplate;

    private Long skillId;

    private Long scriptId;

    private Integer status;

    private Integer sortOrder;
}
