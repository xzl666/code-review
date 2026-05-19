package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class AiGeneratedScriptResponse {

    private String scriptName;

    private String scriptCode;

    private String scriptLanguage;

    private String scriptContent;

    private String parameterTemplate;

    private Integer timeoutSeconds;

    private String ruleName;

    private String ruleCode;

    private String ruleType;

    private String severity;

    private String projectType;

    private String promptTemplate;
}
