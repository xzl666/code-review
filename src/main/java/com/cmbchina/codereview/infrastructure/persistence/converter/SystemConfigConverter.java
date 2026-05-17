package com.cmbchina.codereview.infrastructure.persistence.converter;

import com.cmbchina.codereview.domain.config.SystemConfig;
import com.cmbchina.codereview.infrastructure.persistence.entity.SystemConfigEntity;

public final class SystemConfigConverter {

    private SystemConfigConverter() {
    }

    public static SystemConfig toDomain(SystemConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        SystemConfig config = new SystemConfig();
        config.setId(entity.getId());
        config.setConfigKey(entity.getConfigKey());
        config.setConfigValue(entity.getConfigValue());
        config.setConfigDesc(entity.getConfigDesc());
        return config;
    }

    public static SystemConfigEntity toEntity(SystemConfig config) {
        if (config == null) {
            return null;
        }
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setId(config.getId());
        entity.setConfigKey(config.getConfigKey());
        entity.setConfigValue(config.getConfigValue());
        entity.setConfigDesc(config.getConfigDesc());
        return entity;
    }
}
