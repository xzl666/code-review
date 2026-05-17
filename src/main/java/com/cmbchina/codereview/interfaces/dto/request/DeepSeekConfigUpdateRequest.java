package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeepSeekConfigUpdateRequest {

    @NotBlank(message = "cannot be blank")
    private String apiKey;

    private String url;

    private String model;
}
