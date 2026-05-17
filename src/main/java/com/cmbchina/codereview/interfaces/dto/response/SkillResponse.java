package com.cmbchina.codereview.interfaces.dto.response;

import lombok.Data;

@Data
public class SkillResponse {

    private Long id;

    private String skillName;

    private String skillCode;

    private String functionName;

    private String functionDescription;

    private String parametersSchema;

    private String version;

    private Integer status;
}
