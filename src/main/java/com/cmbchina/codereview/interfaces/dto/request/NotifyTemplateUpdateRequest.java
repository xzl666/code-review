package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotifyTemplateUpdateRequest {

    @NotNull(message = "cannot be null")
    private Long id;

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
