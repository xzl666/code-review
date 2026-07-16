package com.cmbchina.codereview.infrastructure.ocr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "code-review.ocr")
public class OpenCodeReviewProperties {

    private String command;

    private String bundledVersion = "1.7.9";

    private String extractRoot;

    private Integer concurrency = 4;

    private Integer timeoutMinutes = 10;

    private Integer processTimeoutMinutes = 15;

    private String sessionRoot;
}
