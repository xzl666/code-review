package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class ScriptTestRunResponse {

    private Boolean success;

    private Integer exitCode;

    private String stdout;

    private String stderr;

    private Boolean timeout;
}
