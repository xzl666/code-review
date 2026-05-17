package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DefaultTokenUpdateRequest {

    @NotBlank(message = "不能为空")
    private String token;
}
