package com.cmbchina.codereview.interfaces.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModelConfigSaveRequest {

    private Long id;

    @NotBlank(message = "配置名称不能为空")
    private String configName;

    private String providerType;

    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    private String apiKey;

    private Integer enabled;

    private String remark;
}
