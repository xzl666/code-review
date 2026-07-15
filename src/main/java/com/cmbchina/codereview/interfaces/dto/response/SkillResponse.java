package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class SkillResponse {

    private Long id;

    private String skillName;

    private String skillCode;

    private String version;

    private String projectType;

    private Integer ruleMatchingEnabled;

    private String matchRules;

    private String reviewGuidelines;

    private Integer status;
}
