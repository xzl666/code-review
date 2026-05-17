package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotifyConfigUpdateRequest {

    @NotNull(message = "cannot be null")
    private Long id;

    @NotBlank(message = "cannot be blank")
    private String configName;

    private String channelType;

    @NotBlank(message = "cannot be blank")
    private String webhookUrl;

    private String secret;

    private Integer enabled;
}
