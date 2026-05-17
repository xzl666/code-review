package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IdRequest {

    @NotNull(message = "不能为空")
    private Long id;
}
