package com.cmbchina.codereview.domain.config;

public interface SystemConfigRepository {

    SystemConfig findByKey(String configKey);

    void saveOrUpdate(SystemConfig systemConfig);
}
