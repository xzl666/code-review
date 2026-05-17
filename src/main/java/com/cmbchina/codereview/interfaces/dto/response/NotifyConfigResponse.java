package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class NotifyConfigResponse {

    private Long id;

    private String configName;

    private String channelType;

    private String webhookUrl;

    private String secretMasked;

    private Integer enabled;
}
