package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.common.enums.RuleKind;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.infrastructure.persistence.entity.AiSkillEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ScriptRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.AiSkillMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewRuleMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ScriptRuleMapper;
import com.cmbchina.codereview.interfaces.dto.request.RuleCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.RulePageRequest;
import com.cmbchina.codereview.interfaces.dto.request.RuleUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.RuleResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RuleAppService {

    private final ReviewRuleMapper reviewRuleMapper;

    private final AiSkillMapper aiSkillMapper;

    private final ScriptRuleMapper scriptRuleMapper;

    public RuleAppService(ReviewRuleMapper reviewRuleMapper, AiSkillMapper aiSkillMapper, ScriptRuleMapper scriptRuleMapper) {
        this.reviewRuleMapper = reviewRuleMapper;
        this.aiSkillMapper = aiSkillMapper;
        this.scriptRuleMapper = scriptRuleMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(RuleCreateRequest request) {
        validateRuleBinding(request.getRuleKind(), request.getSkillId(), request.getScriptId());
        ReviewRuleEntity entity = new ReviewRuleEntity();
        entity.setRuleName(request.getRuleName());
        entity.setRuleCode(request.getRuleCode());
        entity.setRuleKind(request.getRuleKind());
        entity.setRuleType(request.getRuleType());
        entity.setSeverity(request.getSeverity());
        entity.setProjectType(defaultIfBlank(request.getProjectType(), "ALL"));
        entity.setPromptTemplate(request.getPromptTemplate());
        entity.setSkillId(request.getSkillId());
        entity.setScriptId(request.getScriptId());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setStatus(BaseStatus.ENABLED.getValue());
        reviewRuleMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(RuleUpdateRequest request) {
        ensureExists(request.getId());
        validateRuleBinding(request.getRuleKind(), request.getSkillId(), request.getScriptId());
        ReviewRuleEntity entity = new ReviewRuleEntity();
        entity.setId(request.getId());
        entity.setRuleName(request.getRuleName());
        entity.setRuleCode(request.getRuleCode());
        entity.setRuleKind(request.getRuleKind());
        entity.setRuleType(request.getRuleType());
        entity.setSeverity(request.getSeverity());
        entity.setProjectType(defaultIfBlank(request.getProjectType(), "ALL"));
        entity.setPromptTemplate(request.getPromptTemplate());
        entity.setSkillId(request.getSkillId());
        entity.setScriptId(request.getScriptId());
        entity.setStatus(request.getStatus() == null ? BaseStatus.ENABLED.getValue() : request.getStatus());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        reviewRuleMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensureExists(id);
        reviewRuleMapper.deleteById(id);
    }

    public RuleResponse detail(Long id) {
        return toResponse(ensureExists(id));
    }

    public PageResponse<RuleResponse> page(RulePageRequest request) {
        long pageNo = request.getPageNo() == null ? 1L : request.getPageNo();
        long pageSize = request.getPageSize() == null ? 10L : request.getPageSize();
        LambdaQueryWrapper<ReviewRuleEntity> wrapper = new LambdaQueryWrapper<ReviewRuleEntity>()
            .like(StringUtils.hasText(request.getRuleName()), ReviewRuleEntity::getRuleName, request.getRuleName())
            .eq(StringUtils.hasText(request.getRuleKind()), ReviewRuleEntity::getRuleKind, request.getRuleKind())
            .eq(StringUtils.hasText(request.getRuleType()), ReviewRuleEntity::getRuleType, request.getRuleType())
            .eq(StringUtils.hasText(request.getProjectType()), ReviewRuleEntity::getProjectType, request.getProjectType())
            .eq(request.getStatus() != null, ReviewRuleEntity::getStatus, request.getStatus())
            .orderByAsc(ReviewRuleEntity::getSortOrder)
            .orderByDesc(ReviewRuleEntity::getCreateTime);
        Page<ReviewRuleEntity> page = reviewRuleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<RuleResponse> records = page.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
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

    private void validateRuleBinding(String ruleKind, Long skillId, Long scriptId) {
        if (RuleKind.AI.name().equalsIgnoreCase(ruleKind)) {
            if (skillId == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "AI 规则必须绑定 Skill");
            }
            AiSkillEntity skill = aiSkillMapper.selectById(skillId);
            if (skill == null || skill.getStatus() == null || skill.getStatus() != BaseStatus.ENABLED.getValue()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "绑定的 Skill 不存在或未启用");
            }
        }
        if (RuleKind.SCRIPT.name().equalsIgnoreCase(ruleKind)) {
            if (scriptId == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "脚本规则必须绑定脚本");
            }
            ScriptRuleEntity script = scriptRuleMapper.selectById(scriptId);
            if (script == null || script.getStatus() == null || script.getStatus() != BaseStatus.ENABLED.getValue()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "绑定的脚本不存在或未启用");
            }
        }
    }

    private void updateStatus(Long id, Integer status) {
        ensureExists(id);
        LambdaUpdateWrapper<ReviewRuleEntity> wrapper = new LambdaUpdateWrapper<ReviewRuleEntity>()
            .eq(ReviewRuleEntity::getId, id)
            .set(ReviewRuleEntity::getStatus, status);
        reviewRuleMapper.update(null, wrapper);
    }

    private ReviewRuleEntity ensureExists(Long id) {
        ReviewRuleEntity entity = reviewRuleMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "规则不存在");
        }
        return entity;
    }

    private RuleResponse toResponse(ReviewRuleEntity entity) {
        RuleResponse response = new RuleResponse();
        response.setId(entity.getId());
        response.setRuleName(entity.getRuleName());
        response.setRuleCode(entity.getRuleCode());
        response.setRuleKind(entity.getRuleKind());
        response.setRuleType(entity.getRuleType());
        response.setSeverity(entity.getSeverity());
        response.setProjectType(entity.getProjectType());
        response.setPromptTemplate(entity.getPromptTemplate());
        response.setSkillId(entity.getSkillId());
        response.setScriptId(entity.getScriptId());
        response.setStatus(entity.getStatus());
        response.setSortOrder(entity.getSortOrder());
        return response;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
