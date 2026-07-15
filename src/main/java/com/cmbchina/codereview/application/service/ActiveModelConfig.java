package com.cmbchina.codereview.application.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ActiveModelConfig {

    private String apiKey;

    private String baseUrl;

    private String modelName;
}
