package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;

@Data
public class GiteeSshConfigUpdateRequest {

    private String baseUrl;

    private String privateKey;
}
