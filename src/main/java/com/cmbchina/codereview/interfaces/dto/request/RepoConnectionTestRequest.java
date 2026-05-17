package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.Min;
import lombok.Data;

@Data
public class RepoConnectionTestRequest {

    private Long projectId;

    private String repoUrl;

    private String branch;

    private String projectToken;

    private Integer useDefaultToken;

    @Min(value = 1, message = "必须大于 0")
    private Integer timeoutSeconds = 20;
}
