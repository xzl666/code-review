package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotifyConfigCreateRequest {

    @NotBlank(message = "cannot be blank")
    private String configName;

    private String channelType;

    @NotBlank(message = "cannot be blank")
    private String webhookUrl;

    private String secret;

    private Integer enabled;
}
