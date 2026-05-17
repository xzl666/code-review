package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class ScriptResponse {

    private Long id;

    private String scriptName;

    private String scriptCode;

    private String scriptLanguage;

    private String scriptContent;

    private String parameterTemplate;

    private Integer timeoutSeconds;

    private Integer generatedByAi;

    private Integer status;
}
