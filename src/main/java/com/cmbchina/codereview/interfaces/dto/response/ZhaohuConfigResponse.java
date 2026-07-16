package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class ZhaohuConfigResponse {

    private Integer enabled;
    private String apiHost;
    private String clientId;
    private boolean clientSecretConfigured;
    private String clientSecretMasked;
    private String robotId;
    private String appBaseUrl;
    private Integer tokenExpireSeconds;
    private Integer tokenBufferSeconds;
    private Integer timeoutSeconds;
}
