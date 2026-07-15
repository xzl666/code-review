package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ScriptPageRequest extends PageRequest {

    private String scriptName;

    private String scriptCode;

    private String projectType;

    private Integer status;
}
