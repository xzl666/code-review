package com.cmbchina.codereview.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemUserResponse {
    private String userName;
    private String userId;
    private String employeeId;
}
