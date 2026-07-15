package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.infrastructure.persistence.entity.AiSkillEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.AiSkillMapper;
import com.cmbchina.codereview.interfaces.dto.request.SkillCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.SkillPageRequest;
import com.cmbchina.codereview.interfaces.dto.request.SkillUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.request.ValidateSchemaRequest;
import com.cmbchina.codereview.interfaces.dto.response.SchemaValidateResponse;
import com.cmbchina.codereview.interfaces.dto.response.SkillResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SkillAppService {

    private final AiSkillMapper aiSkillMapper;

    private final ObjectMapper objectMapper;

    public SkillAppService(AiSkillMapper aiSkillMapper, ObjectMapper objectMapper) {
        this.aiSkillMapper = aiSkillMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SkillCreateRequest request) {
        AiSkillEntity entity = new AiSkillEntity();
        entity.setSkillName(request.getSkillName());
        entity.setSkillCode(request.getSkillCode());
        entity.setVersion(defaultIfBlank(request.getVersion(), "1.0.0"));
        entity.setProjectType(normalizeProjectType(request.getProjectType()));
        entity.setRuleMatchingEnabled(request.getRuleMatchingEnabled() == null ? 0 : request.getRuleMatchingEnabled());
        entity.setMatchRules(request.getMatchRules());
        entity.setReviewGuidelines(request.getReviewGuidelines());
        entity.setStatus(BaseStatus.ENABLED.getValue());
        aiSkillMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SkillUpdateRequest request) {
        ensureExists(request.getId());
        AiSkillEntity entity = new AiSkillEntity();
        entity.setId(request.getId());
        entity.setSkillName(request.getSkillName());
        entity.setSkillCode(request.getSkillCode());
        entity.setVersion(defaultIfBlank(request.getVersion(), "1.0.0"));
        entity.setProjectType(normalizeProjectType(request.getProjectType()));
        entity.setRuleMatchingEnabled(request.getRuleMatchingEnabled() == null ? 0 : request.getRuleMatchingEnabled());
        entity.setMatchRules(request.getMatchRules());
        entity.setReviewGuidelines(request.getReviewGuidelines());
        entity.setStatus(request.getStatus() == null ? BaseStatus.ENABLED.getValue() : request.getStatus());
        aiSkillMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensureExists(id);
        aiSkillMapper.deleteById(id);
    }

    public SkillResponse detail(Long id) {
        return toResponse(ensureExists(id));
    }

    public PageResponse<SkillResponse> page(SkillPageRequest request) {
        long pageNo = request.getPageNo() == null ? 1L : request.getPageNo();
        long pageSize = request.getPageSize() == null ? 10L : request.getPageSize();
        LambdaQueryWrapper<AiSkillEntity> wrapper = new LambdaQueryWrapper<AiSkillEntity>()
            .like(StringUtils.hasText(request.getSkillName()), AiSkillEntity::getSkillName, request.getSkillName())
            .like(StringUtils.hasText(request.getSkillCode()), AiSkillEntity::getSkillCode, request.getSkillCode())
            .eq(StringUtils.hasText(request.getProjectType()), AiSkillEntity::getProjectType, request.getProjectType())
            .eq(request.getStatus() != null, AiSkillEntity::getStatus, request.getStatus())
            .orderByDesc(AiSkillEntity::getCreateTime);
        Page<AiSkillEntity> page = aiSkillMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<SkillResponse> records = page.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), pageNo, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        updateStatus(id, BaseStatus.ENABLED.getValue());
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        updateStatus(id, BaseStatus.DISABLED.getValue());
    }

    public SchemaValidateResponse validateSchema(ValidateSchemaRequest request) {
        return validateSchema(request.getParametersSchema());
    }

    public SchemaValidateResponse validateSchema(String schemaText) {
        try {
            JsonNode root = objectMapper.readTree(schemaText);
            if (!root.isObject()) {
                return new SchemaValidateResponse(false, "JSON Schema 根节点必须是对象");
            }
            JsonNode type = root.get("type");
            if (type == null || !"object".equals(type.asText())) {
                return new SchemaValidateResponse(false, "Function Calling parameters schema 的 type 必须为 object");
            }
            if (!root.has("properties") || !root.get("properties").isObject()) {
                return new SchemaValidateResponse(false, "JSON Schema 必须包含 properties 对象");
            }
            return new SchemaValidateResponse(true, "JSON Schema 校验通过");
        } catch (Exception exception) {
            return new SchemaValidateResponse(false, "JSON Schema 格式错误：" + exception.getMessage());
        }
    }

    private void updateStatus(Long id, Integer status) {
        ensureExists(id);
        LambdaUpdateWrapper<AiSkillEntity> wrapper = new LambdaUpdateWrapper<AiSkillEntity>()
            .eq(AiSkillEntity::getId, id)
            .set(AiSkillEntity::getStatus, status);
        aiSkillMapper.update(null, wrapper);
    }

    private AiSkillEntity ensureExists(Long id) {
        AiSkillEntity entity = aiSkillMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Skill 不存在");
        }
        return entity;
    }

    private SkillResponse toResponse(AiSkillEntity entity) {
        SkillResponse response = new SkillResponse();
        response.setId(entity.getId());
        response.setSkillName(entity.getSkillName());
        response.setSkillCode(entity.getSkillCode());
        response.setVersion(entity.getVersion());
        response.setProjectType(defaultIfBlank(entity.getProjectType(), "ALL"));
        response.setRuleMatchingEnabled(entity.getRuleMatchingEnabled() == null ? 0 : entity.getRuleMatchingEnabled());
        response.setMatchRules(entity.getMatchRules());
        response.setReviewGuidelines(entity.getReviewGuidelines());
        response.setStatus(entity.getStatus());
        return response;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String normalizeProjectType(String projectType) {
        String value = defaultIfBlank(projectType, "ALL").toUpperCase();
        if (!"ALL".equals(value) && !"FRONTEND".equals(value) && !"BACKEND".equals(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "projectType must be ALL, FRONTEND or BACKEND");
        }
        return value;
    }
}
