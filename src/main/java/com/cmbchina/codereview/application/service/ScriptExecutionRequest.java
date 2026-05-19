package com.cmbchina.codereview.application.service;

import lombok.Data;

@Data
public class ScriptExecutionRequest {

    private String language;

    private String content;

    private String inputJson;

    private Integer timeoutSeconds;

    private Integer maxOutputChars;
}
