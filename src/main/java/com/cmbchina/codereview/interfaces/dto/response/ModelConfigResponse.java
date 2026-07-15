package com.cmbchina.codereview.interfaces.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ModelConfigResponse {

    private Long id;

    private String configName;

    private String providerType;

    private String baseUrl;

    private String modelName;

    private Boolean configured;

    private String maskedApiKey;

    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
