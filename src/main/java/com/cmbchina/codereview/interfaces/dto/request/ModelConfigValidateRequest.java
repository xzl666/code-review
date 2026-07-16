package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;

@Data
public class ModelConfigValidateRequest {

    private Long id;

    private String apiKey;

    private String baseUrl;

    private String modelName;

    private String providerType;
}
