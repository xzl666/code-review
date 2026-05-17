package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class DefaultTokenResponse {

    private Boolean configured;

    private String maskedToken;
}
