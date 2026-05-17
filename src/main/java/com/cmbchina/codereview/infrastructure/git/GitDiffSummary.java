package com.cmbchina.codereview.infrastructure.git;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class GitDiffSummary {

    private Integer commitCount = 0;

    private Integer diffFileCount = 0;

    private List<String> filePaths = Collections.emptyList();

    private String diffContent = "";
}
