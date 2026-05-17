package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotifyTemplateCreateRequest {

    @NotBlank(message = "cannot be blank")
    private String templateName;

    @NotBlank(message = "cannot be blank")
    private String templateCode;

    private String channelType;

    @NotBlank(message = "cannot be blank")
    private String eventType;

    @NotBlank(message = "cannot be blank")
    private String templateContent;

    private Integer enabled;
}
