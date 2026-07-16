package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.domain.config.SystemConfig;
import com.cmbchina.codereview.domain.config.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeConfigService {

    private final SystemConfigRepository systemConfigRepository;

    public RuntimeConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    public String getString(String key, String fallback) {
        SystemConfig config = systemConfigRepository.findByKey(key);
        return config != null && StringUtils.hasText(config.getConfigValue())
            ? config.getConfigValue().trim() : fallback;
    }

    public boolean getBoolean(String key, Boolean fallback) {
        String value = getString(key, null);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value) : Boolean.TRUE.equals(fallback);
    }

    public int getPositiveInt(String key, Integer fallback, int defaultValue) {
        String value = getString(key, null);
        try {
            int parsed = StringUtils.hasText(value) ? Integer.parseInt(value) : fallback == null ? defaultValue : fallback;
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return fallback != null && fallback > 0 ? fallback : defaultValue;
        }
    }

    public void save(String key, String value, String description) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value == null ? "" : value.trim());
        config.setConfigDesc(description);
        systemConfigRepository.saveOrUpdate(config);
    }
}
