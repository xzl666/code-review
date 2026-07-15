package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class ScriptResponse {

    private Long id;

    private String scriptName;

    private String scriptCode;

    private String projectType;

    private String ruleType;

    private String severity;

    private String description;

    private String scriptContent;

    private Integer timeoutSeconds;

    private Integer status;

    private Integer sortOrder;
}
