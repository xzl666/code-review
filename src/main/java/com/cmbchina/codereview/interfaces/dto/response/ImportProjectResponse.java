package com.cmbchina.codereview.interfaces.dto.response;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ImportProjectResponse {

    private int successCount;

    private int failureCount;

    private List<String> failureReasons = new ArrayList<>();

    public void addSuccess() {
        successCount++;
    }

    public void addFailure(String reason) {
        failureCount++;
        failureReasons.add(reason);
    }
}
