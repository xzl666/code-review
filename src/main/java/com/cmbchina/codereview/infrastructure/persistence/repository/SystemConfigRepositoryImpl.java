package com.cmbchina.codereview.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cmbchina.codereview.domain.config.SystemConfig;
import com.cmbchina.codereview.domain.config.SystemConfigRepository;
import com.cmbchina.codereview.infrastructure.persistence.converter.SystemConfigConverter;
import com.cmbchina.codereview.infrastructure.persistence.entity.SystemConfigEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.SystemConfigMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SystemConfigRepositoryImpl implements SystemConfigRepository {

    private final SystemConfigMapper systemConfigMapper;

    public SystemConfigRepositoryImpl(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    @Override
    public SystemConfig findByKey(String configKey) {
        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<SystemConfigEntity>()
            .eq(SystemConfigEntity::getConfigKey, configKey)
            .last("LIMIT 1");
        return SystemConfigConverter.toDomain(systemConfigMapper.selectOne(wrapper));
    }

    @Override
    public void saveOrUpdate(SystemConfig systemConfig) {
        SystemConfig existing = findByKey(systemConfig.getConfigKey());
        SystemConfigEntity entity = SystemConfigConverter.toEntity(systemConfig);
        if (existing == null) {
            systemConfigMapper.insert(entity);
            return;
        }
        entity.setId(existing.getId());
        systemConfigMapper.updateById(entity);
    }
}
