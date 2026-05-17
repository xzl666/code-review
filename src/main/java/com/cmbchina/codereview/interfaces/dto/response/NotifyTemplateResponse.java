package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class NotifyTemplateResponse {

    private Long id;

    private String templateName;

    private String templateCode;

    private String channelType;

    private String eventType;

    private String templateContent;

    private Integer enabled;
}
