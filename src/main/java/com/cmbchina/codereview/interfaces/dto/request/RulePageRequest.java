package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RulePageRequest extends PageRequest {

    private String ruleName;

    private String ruleKind;

    private String ruleType;

    private String projectType;

    private Integer status;
}
