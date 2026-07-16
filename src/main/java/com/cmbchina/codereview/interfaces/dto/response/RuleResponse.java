package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class RuleResponse {

    private Long id;

    private String ruleName;

    private String ruleCode;

    private String promptTemplate;

    private String pathPattern;

    private Integer mergeSystemRule;

    private Integer status;

    private Integer sortOrder;
}
