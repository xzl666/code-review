package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectCommitListRequest {

    @NotNull(message = "项目不能为空")
    private Long projectId;

    private String branch;

    @Min(value = 1, message = "提交数量必须大于 0")
    @Max(value = 200, message = "提交数量不能超过 200")
    private Integer limit = 100;
}
