package com.cmbchina.codereview.domain.config;

import lombok.Data;

@Data
public class SystemConfig {

    private Long id;

    private String configKey;

    private String configValue;

    private String configDesc;
}
