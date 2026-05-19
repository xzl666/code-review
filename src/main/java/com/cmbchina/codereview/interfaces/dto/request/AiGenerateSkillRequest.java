package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;

@Data
public class AiGenerateSkillRequest {

    private String requirement;

    private String projectType;

    private String ruleType;

    private String severity;
}
