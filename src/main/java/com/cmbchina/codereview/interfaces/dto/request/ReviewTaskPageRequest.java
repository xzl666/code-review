package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewTaskPageRequest extends PageRequest {

    private Long projectId;

    private String projectName;

    private String status;

    private String triggerType;
}
