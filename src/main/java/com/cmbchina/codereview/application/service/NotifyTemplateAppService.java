package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.infrastructure.persistence.entity.NotifyTemplateEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.NotifyTemplateMapper;
import com.cmbchina.codereview.interfaces.dto.request.NotifyTemplateCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyTemplatePageRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyTemplatePreviewRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyTemplateUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.NotifyTemplatePreviewResponse;
import com.cmbchina.codereview.interfaces.dto.response.NotifyTemplateResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NotifyTemplateAppService {

    private static final String WEBHOOK = "WEBHOOK";

    private final NotifyTemplateMapper notifyTemplateMapper;

    public NotifyTemplateAppService(NotifyTemplateMapper notifyTemplateMapper) {
        this.notifyTemplateMapper = notifyTemplateMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(NotifyTemplateCreateRequest request) {
        ensureCodeUnique(request.getTemplateCode(), null);
        NotifyTemplateEntity entity = new NotifyTemplateEntity();
        entity.setTemplateName(request.getTemplateName());
        entity.setTemplateCode(request.getTemplateCode());
        entity.setChannelType(normalizeChannel(request.getChannelType()));
        entity.setEventType(request.getEventType());
        entity.setTemplateContent(request.getTemplateContent());
        entity.setEnabled(request.getEnabled() == null ? BaseStatus.ENABLED.getValue() : request.getEnabled());
        notifyTemplateMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(NotifyTemplateUpdateRequest request) {
        ensureExists(request.getId());
        ensureCodeUnique(request.getTemplateCode(), request.getId());
        NotifyTemplateEntity entity = new NotifyTemplateEntity();
        entity.setId(request.getId());
        entity.setTemplateName(request.getTemplateName());
        entity.setTemplateCode(request.getTemplateCode());
        entity.setChannelType(normalizeChannel(request.getChannelType()));
        entity.setEventType(request.getEventType());
        entity.setTemplateContent(request.getTemplateContent());
        entity.setEnabled(request.getEnabled() == null ? BaseStatus.ENABLED.getValue() : request.getEnabled());
        notifyTemplateMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensureExists(id);
        notifyTemplateMapper.deleteById(id);
    }

    public NotifyTemplateResponse detail(Long id) {
        return toResponse(ensureExists(id));
    }

    public PageResponse<NotifyTemplateResponse> page(NotifyTemplatePageRequest request) {
        long pageNo = request.getPageNo() == null ? 1L : request.getPageNo();
        long pageSize = request.getPageSize() == null ? 10L : request.getPageSize();
        LambdaQueryWrapper<NotifyTemplateEntity> wrapper = new LambdaQueryWrapper<NotifyTemplateEntity>()
            .like(StringUtils.hasText(request.getTemplateName()), NotifyTemplateEntity::getTemplateName, request.getTemplateName())
            .like(StringUtils.hasText(request.getTemplateCode()), NotifyTemplateEntity::getTemplateCode, request.getTemplateCode())
            .eq(StringUtils.hasText(request.getChannelType()), NotifyTemplateEntity::getChannelType, request.getChannelType())
            .eq(StringUtils.hasText(request.getEventType()), NotifyTemplateEntity::getEventType, request.getEventType())
            .eq(request.getEnabled() != null, NotifyTemplateEntity::getEnabled, request.getEnabled())
            .orderByDesc(NotifyTemplateEntity::getCreateTime);
        Page<NotifyTemplateEntity> page = notifyTemplateMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<NotifyTemplateResponse> records = page.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
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

    public NotifyTemplatePreviewResponse preview(NotifyTemplatePreviewRequest request) {
        String content = request.getTemplateContent();
        if (request.getTemplateId() != null) {
            content = ensureExists(request.getTemplateId()).getTemplateContent();
        }
        if (!StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "templateContent cannot be blank");
        }
        return new NotifyTemplatePreviewResponse(render(content, request.getVariables()));
    }

    private String render(String content, Map<String, Object> variables) {
        Map<String, Object> safeVariables = variables == null ? Collections.emptyMap() : variables;
        String result = content;
        for (Map.Entry<String, Object> entry : safeVariables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            String legacyPlaceholder = "{{" + entry.getKey() + "}}";
            result = result.replace(legacyPlaceholder, entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private void updateStatus(Long id, Integer enabled) {
        ensureExists(id);
        LambdaUpdateWrapper<NotifyTemplateEntity> wrapper = new LambdaUpdateWrapper<NotifyTemplateEntity>()
            .eq(NotifyTemplateEntity::getId, id)
            .set(NotifyTemplateEntity::getEnabled, enabled);
        notifyTemplateMapper.update(null, wrapper);
    }

    private NotifyTemplateEntity ensureExists(Long id) {
        NotifyTemplateEntity entity = notifyTemplateMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "notify template not found");
        }
        return entity;
    }

    private void ensureCodeUnique(String templateCode, Long excludeId) {
        LambdaQueryWrapper<NotifyTemplateEntity> wrapper = new LambdaQueryWrapper<NotifyTemplateEntity>()
            .eq(NotifyTemplateEntity::getTemplateCode, templateCode)
            .ne(excludeId != null, NotifyTemplateEntity::getId, excludeId);
        if (notifyTemplateMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "templateCode already exists");
        }
    }

    private NotifyTemplateResponse toResponse(NotifyTemplateEntity entity) {
        NotifyTemplateResponse response = new NotifyTemplateResponse();
        response.setId(entity.getId());
        response.setTemplateName(entity.getTemplateName());
        response.setTemplateCode(entity.getTemplateCode());
        response.setChannelType(entity.getChannelType());
        response.setEventType(entity.getEventType());
        response.setTemplateContent(entity.getTemplateContent());
        response.setEnabled(entity.getEnabled());
        return response;
    }

    private String normalizeChannel(String channelType) {
        String value = StringUtils.hasText(channelType) ? channelType.toUpperCase() : WEBHOOK;
        if (!WEBHOOK.equals(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "channelType only supports WEBHOOK");
        }
        return value;
    }
}
