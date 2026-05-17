package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.infrastructure.persistence.entity.NotifyConfigEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.NotifyConfigMapper;
import com.cmbchina.codereview.interfaces.dto.request.NotifyConfigCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyConfigPageRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyConfigTestSendRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyConfigUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.NotifyConfigResponse;
import com.cmbchina.codereview.interfaces.dto.response.NotifyTestSendResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class NotifyConfigAppService {

    private static final String WEBHOOK = "WEBHOOK";

    private final NotifyConfigMapper notifyConfigMapper;

    private final ObjectMapper objectMapper;

    public NotifyConfigAppService(NotifyConfigMapper notifyConfigMapper, ObjectMapper objectMapper) {
        this.notifyConfigMapper = notifyConfigMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(NotifyConfigCreateRequest request) {
        NotifyConfigEntity entity = new NotifyConfigEntity();
        entity.setConfigName(request.getConfigName());
        entity.setChannelType(normalizeChannel(request.getChannelType()));
        entity.setWebhookUrl(request.getWebhookUrl());
        entity.setSecretEncrypt(request.getSecret());
        entity.setEnabled(request.getEnabled() == null ? BaseStatus.ENABLED.getValue() : request.getEnabled());
        notifyConfigMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(NotifyConfigUpdateRequest request) {
        ensureExists(request.getId());
        NotifyConfigEntity entity = new NotifyConfigEntity();
        entity.setId(request.getId());
        entity.setConfigName(request.getConfigName());
        entity.setChannelType(normalizeChannel(request.getChannelType()));
        entity.setWebhookUrl(request.getWebhookUrl());
        entity.setSecretEncrypt(request.getSecret());
        entity.setEnabled(request.getEnabled() == null ? BaseStatus.ENABLED.getValue() : request.getEnabled());
        notifyConfigMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensureExists(id);
        notifyConfigMapper.deleteById(id);
    }

    public NotifyConfigResponse detail(Long id) {
        return toResponse(ensureExists(id));
    }

    public PageResponse<NotifyConfigResponse> page(NotifyConfigPageRequest request) {
        long pageNo = request.getPageNo() == null ? 1L : request.getPageNo();
        long pageSize = request.getPageSize() == null ? 10L : request.getPageSize();
        LambdaQueryWrapper<NotifyConfigEntity> wrapper = new LambdaQueryWrapper<NotifyConfigEntity>()
            .like(StringUtils.hasText(request.getConfigName()), NotifyConfigEntity::getConfigName, request.getConfigName())
            .eq(StringUtils.hasText(request.getChannelType()), NotifyConfigEntity::getChannelType, request.getChannelType())
            .eq(request.getEnabled() != null, NotifyConfigEntity::getEnabled, request.getEnabled())
            .orderByDesc(NotifyConfigEntity::getCreateTime);
        Page<NotifyConfigEntity> page = notifyConfigMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<NotifyConfigResponse> records = page.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
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

    public NotifyTestSendResponse testSend(NotifyConfigTestSendRequest request) {
        String webhookUrl = request.getWebhookUrl();
        if (request.getConfigId() != null) {
            NotifyConfigEntity entity = ensureExists(request.getConfigId());
            webhookUrl = entity.getWebhookUrl();
        }
        if (!StringUtils.hasText(webhookUrl)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "webhookUrl cannot be blank");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", defaultIfBlank(request.getTitle(), "Code Review notification test"));
            body.put("content", defaultIfBlank(request.getContent(), "Webhook configuration is reachable."));
            body.put("extra", request.getExtra());
            String bodyText = objectMapper.writeValueAsString(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate().exchange(
                webhookUrl,
                HttpMethod.POST,
                new HttpEntity<>(bodyText, headers),
                String.class
            );
            NotifyTestSendResponse result = new NotifyTestSendResponse();
            result.setSuccess(response.getStatusCode().is2xxSuccessful());
            result.setStatusCode(response.getStatusCodeValue());
            result.setMessage(result.getSuccess() ? "sent" : "webhook returned non-2xx status");
            result.setResponseBody(response.getBody());
            return result;
        } catch (Exception exception) {
            NotifyTestSendResponse result = new NotifyTestSendResponse();
            result.setSuccess(false);
            result.setStatusCode(null);
            result.setMessage(exception.getMessage());
            result.setResponseBody("");
            return result;
        }
    }

    private void updateStatus(Long id, Integer enabled) {
        ensureExists(id);
        LambdaUpdateWrapper<NotifyConfigEntity> wrapper = new LambdaUpdateWrapper<NotifyConfigEntity>()
            .eq(NotifyConfigEntity::getId, id)
            .set(NotifyConfigEntity::getEnabled, enabled);
        notifyConfigMapper.update(null, wrapper);
    }

    private NotifyConfigEntity ensureExists(Long id) {
        NotifyConfigEntity entity = notifyConfigMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "notify config not found");
        }
        return entity;
    }

    private NotifyConfigResponse toResponse(NotifyConfigEntity entity) {
        NotifyConfigResponse response = new NotifyConfigResponse();
        response.setId(entity.getId());
        response.setConfigName(entity.getConfigName());
        response.setChannelType(entity.getChannelType());
        response.setWebhookUrl(entity.getWebhookUrl());
        response.setSecretMasked(mask(entity.getSecretEncrypt()));
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

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
