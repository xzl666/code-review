package com.cmbchina.codereview.infrastructure.git;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GitCommandResult {

    private int exitCode;

    private String stdout;

    private String stderr;
}
