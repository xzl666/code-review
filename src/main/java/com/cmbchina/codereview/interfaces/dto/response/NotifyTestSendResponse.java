package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class NotifyTestSendResponse {

    private Boolean success;

    private Integer statusCode;

    private String message;

    private String responseBody;
}
