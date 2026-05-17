package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class DeepSeekConfigResponse {

    private Boolean configured;

    private String maskedApiKey;

    private String url;

    private String model;
}
