package com.cmbchina.codereview.application.service;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ScriptRuleExecutionResult {

    private Integer issueCount = 0;

    private List<String> warnings = new ArrayList<>();

    public void addIssueCount(int count) {
        issueCount += count;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }
}
