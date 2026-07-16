package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModelConfigSaveRequest {

    private Long id;

    private String configName;

    private String providerType;

    private String baseUrl;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    private String apiKey;

    private Integer enabled;

    private String remark;
}
