package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SkillPageRequest extends PageRequest {

    private String skillName;

    private String skillCode;

    private Integer status;
}
