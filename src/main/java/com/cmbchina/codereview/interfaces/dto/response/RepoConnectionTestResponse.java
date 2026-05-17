package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class RepoConnectionTestResponse {

    private Boolean success;

    private String message;

    private String branch;
}
