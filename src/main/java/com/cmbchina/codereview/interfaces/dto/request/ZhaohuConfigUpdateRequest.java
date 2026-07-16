package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ZhaohuConfigUpdateRequest {

    @NotNull
    private Integer enabled;

    @NotBlank
    private String apiHost;

    @NotBlank
    private String clientId;

    private String clientSecret;

    @NotBlank
    private String robotId;

    @NotBlank
    private String appBaseUrl;

    @NotNull
    @Min(60)
    private Integer tokenExpireSeconds;

    @NotNull
    @Min(0)
    private Integer tokenBufferSeconds;

    @NotNull
    @Min(1)
    @Max(120)
    private Integer timeoutSeconds;
}
