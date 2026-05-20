package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class AiGeneratedSkillResponse {

    private String skillName;

    private String skillCode;

    private String functionName;

    private String functionDescription;

    private String parametersSchema;

    private String version;

    private String ruleName;

    private String ruleCode;

    private String ruleType;

    private String severity;

    private String projectType;

    private Integer ruleMatchingEnabled;

    private String matchRules;

    private String promptTemplate;
}
