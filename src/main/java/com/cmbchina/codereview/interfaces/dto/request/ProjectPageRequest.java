package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.Min;
import lombok.Data;

@Data
public class ProjectPageRequest {

    private String projectName;

    private String projectType;

    private Integer status;

    @Min(value = 1, message = "必须大于 0")
    private Long pageNo = 1L;

    @Min(value = 1, message = "必须大于 0")
    private Long pageSize = 10L;
}
