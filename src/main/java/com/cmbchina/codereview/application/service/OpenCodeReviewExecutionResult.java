package com.cmbchina.codereview.application.service;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class OpenCodeReviewExecutionResult {

    private Integer invocationCount = 0;

    private Integer aiCallCount = 0;

    private Integer aiSuccessCount = 0;

    private Integer aiFailureCount = 0;

    private Long inputTokenCount = 0L;

    private Long outputTokenCount = 0L;

    private Long totalTokenCount = 0L;

    private Long cacheReadTokenCount = 0L;

    private Long cacheWriteTokenCount = 0L;

    private Integer issueCount = 0;

    private Integer reviewedFileCount = 0;

    private List<String> warnings = new ArrayList<>();
}
