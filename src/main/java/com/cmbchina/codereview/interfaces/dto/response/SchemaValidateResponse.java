package com.cmbchina.codereview.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchemaValidateResponse {

    private Boolean valid;

    private String message;
}
