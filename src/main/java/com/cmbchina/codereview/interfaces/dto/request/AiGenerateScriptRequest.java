package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;

@Data
public class AiGenerateScriptRequest {

    private String requirement;

    private String projectType;

    private String ruleType;

    private String severity;

    private String scriptLanguage;
}
