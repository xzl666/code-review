package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.common.enums.ProjectType;
import com.cmbchina.codereview.common.enums.Severity;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.infrastructure.persistence.entity.ScriptRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ScriptRuleMapper;
import com.cmbchina.codereview.interfaces.dto.request.ScriptCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.ScriptPageRequest;
import com.cmbchina.codereview.interfaces.dto.request.ScriptTestRunRequest;
import com.cmbchina.codereview.interfaces.dto.request.ScriptUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.ScriptResponse;
import com.cmbchina.codereview.interfaces.dto.response.ScriptTestRunResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ScriptRuleAppService {

    private final ScriptRuleMapper scriptRuleMapper;

    private final ScriptSandboxExecutor scriptSandboxExecutor;

    public ScriptRuleAppService(ScriptRuleMapper scriptRuleMapper,
                                ScriptSandboxExecutor scriptSandboxExecutor) {
        this.scriptRuleMapper = scriptRuleMapper;
        this.scriptSandboxExecutor = scriptSandboxExecutor;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ScriptCreateRequest request) {
        ScriptRuleEntity entity = new ScriptRuleEntity();
        entity.setScriptName(request.getScriptName());
        entity.setScriptCode(request.getScriptCode());
        entity.setProjectType(normalizeProjectType(request.getProjectType()));
        entity.setRuleType(defaultIfBlank(request.getRuleType(), "CUSTOM"));
        entity.setSeverity(normalizeSeverity(request.getSeverity()));
        entity.setDescription(request.getDescription());
        entity.setScriptContent(request.getScriptContent());
        entity.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 30 : request.getTimeoutSeconds());
        entity.setStatus(BaseStatus.ENABLED.getValue());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        scriptRuleMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ScriptUpdateRequest request) {
        ensureExists(request.getId());
        ScriptRuleEntity entity = new ScriptRuleEntity();
        entity.setId(request.getId());
        entity.setScriptName(request.getScriptName());
        entity.setScriptCode(request.getScriptCode());
        entity.setProjectType(normalizeProjectType(request.getProjectType()));
        entity.setRuleType(defaultIfBlank(request.getRuleType(), "CUSTOM"));
        entity.setSeverity(normalizeSeverity(request.getSeverity()));
        entity.setDescription(request.getDescription());
        entity.setScriptContent(request.getScriptContent());
        entity.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 30 : request.getTimeoutSeconds());
        entity.setStatus(request.getStatus() == null ? BaseStatus.ENABLED.getValue() : request.getStatus());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        scriptRuleMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensureExists(id);
        scriptRuleMapper.deleteById(id);
    }

    public ScriptResponse detail(Long id) {
        return toResponse(ensureExists(id));
    }

    public PageResponse<ScriptResponse> page(ScriptPageRequest request) {
        long pageNo = request.getPageNo() == null ? 1L : request.getPageNo();
        long pageSize = request.getPageSize() == null ? 10L : request.getPageSize();
        LambdaQueryWrapper<ScriptRuleEntity> wrapper = new LambdaQueryWrapper<ScriptRuleEntity>()
            .like(StringUtils.hasText(request.getScriptName()), ScriptRuleEntity::getScriptName, request.getScriptName())
            .like(StringUtils.hasText(request.getScriptCode()), ScriptRuleEntity::getScriptCode, request.getScriptCode())
            .eq(StringUtils.hasText(request.getProjectType()), ScriptRuleEntity::getProjectType, request.getProjectType())
            .eq(request.getStatus() != null, ScriptRuleEntity::getStatus, request.getStatus())
            .orderByAsc(ScriptRuleEntity::getSortOrder)
            .orderByDesc(ScriptRuleEntity::getCreateTime);
        Page<ScriptRuleEntity> page = scriptRuleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ScriptResponse> records = page.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
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

    public ScriptTestRunResponse testRun(ScriptTestRunRequest request) {
        Integer timeoutSeconds = request.getTimeoutSeconds() == null ? 10 : request.getTimeoutSeconds();
        String language;
        String content;
        if (request.getScriptId() != null) {
            ScriptRuleEntity entity = ensureExists(request.getScriptId());
            language = "PYTHON";
            content = entity.getScriptContent();
            timeoutSeconds = entity.getTimeoutSeconds();
        } else {
            language = "PYTHON";
            content = request.getScriptContent();
        }
        ScriptExecutionRequest executionRequest = new ScriptExecutionRequest();
        executionRequest.setLanguage(language);
        executionRequest.setContent(content);
        executionRequest.setInputJson(request.getInputJson());
        executionRequest.setTimeoutSeconds(timeoutSeconds);
        ScriptExecutionResult result = scriptSandboxExecutor.execute(executionRequest);
        validateIssueJson(result);
        return toTestRunResponse(result);
    }

    private void updateStatus(Long id, Integer status) {
        ensureExists(id);
        LambdaUpdateWrapper<ScriptRuleEntity> wrapper = new LambdaUpdateWrapper<ScriptRuleEntity>()
            .eq(ScriptRuleEntity::getId, id)
            .set(ScriptRuleEntity::getStatus, status);
        scriptRuleMapper.update(null, wrapper);
    }

    private ScriptRuleEntity ensureExists(Long id) {
        ScriptRuleEntity entity = scriptRuleMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "脚本不存在");
        }
        return entity;
    }

    private ScriptResponse toResponse(ScriptRuleEntity entity) {
        ScriptResponse response = new ScriptResponse();
        response.setId(entity.getId());
        response.setScriptName(entity.getScriptName());
        response.setScriptCode(entity.getScriptCode());
        response.setProjectType(entity.getProjectType());
        response.setRuleType(entity.getRuleType());
        response.setSeverity(entity.getSeverity());
        response.setDescription(entity.getDescription());
        response.setScriptContent(entity.getScriptContent());
        response.setTimeoutSeconds(entity.getTimeoutSeconds());
        response.setStatus(entity.getStatus());
        response.setSortOrder(entity.getSortOrder());
        return response;
    }

    private String normalizeProjectType(String projectType) {
        String upper = defaultIfBlank(projectType, ProjectType.ALL.name()).toUpperCase();
        try {
            ProjectType.valueOf(upper);
            return upper;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "项目类型必须是 ALL/FRONTEND/BACKEND");
        }
    }

    private String normalizeSeverity(String severity) {
        String upper = defaultIfBlank(severity, Severity.MAJOR.name()).toUpperCase();
        try {
            Severity.valueOf(upper);
            return upper;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "严重等级不合法");
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private void validateIssueJson(ScriptExecutionResult result) {
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getStdout());
            com.fasterxml.jackson.databind.JsonNode issues = root.isArray() ? root : root.get("issues");
            if (issues == null || !issues.isArray()) {
                result.setSuccess(false);
                result.setStderr("脚本输出必须是 JSON，且包含 issues 数组");
            }
        } catch (Exception exception) {
            result.setSuccess(false);
            result.setStderr("脚本输出不是合法 JSON：" + exception.getMessage());
        }
    }

    private ScriptTestRunResponse toTestRunResponse(ScriptExecutionResult result) {
        ScriptTestRunResponse response = new ScriptTestRunResponse();
        response.setSuccess(result.getSuccess());
        response.setExitCode(result.getExitCode());
        response.setStdout(result.getStdout());
        response.setStderr(result.getStderr());
        response.setTimeout(result.getTimeout());
        response.setSecurityBlocked(result.getSecurityBlocked());
        return response;
    }
}
