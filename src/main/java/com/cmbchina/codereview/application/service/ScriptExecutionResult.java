package com.cmbchina.codereview.application.service;

import lombok.Data;

@Data
public class ScriptExecutionResult {

    private Boolean success;

    private Integer exitCode;

    private String stdout;

    private String stderr;

    private Boolean timeout;

    private Boolean securityBlocked;
}
