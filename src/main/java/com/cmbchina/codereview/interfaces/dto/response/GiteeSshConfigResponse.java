package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class GiteeSshConfigResponse {

    private String baseUrl;

    private Boolean privateKeyConfigured;

    private String keyFingerprint;
}
