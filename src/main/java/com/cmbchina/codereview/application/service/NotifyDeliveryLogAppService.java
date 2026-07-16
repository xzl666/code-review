package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.infrastructure.persistence.entity.NotifyDeliveryLogEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.NotifyDeliveryLogMapper;
import com.cmbchina.codereview.interfaces.dto.request.NotifyDeliveryLogPageRequest;
import com.cmbchina.codereview.interfaces.dto.response.NotifyDeliveryLogResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NotifyDeliveryLogAppService {

    private final NotifyDeliveryLogMapper notifyDeliveryLogMapper;

    public NotifyDeliveryLogAppService(NotifyDeliveryLogMapper notifyDeliveryLogMapper) {
        this.notifyDeliveryLogMapper = notifyDeliveryLogMapper;
    }

    public PageResponse<NotifyDeliveryLogResponse> page(NotifyDeliveryLogPageRequest request) {
        long pageNo = request.getPageNo() == null ? 1L : request.getPageNo();
        long pageSize = request.getPageSize() == null ? 10L : request.getPageSize();
        LambdaQueryWrapper<NotifyDeliveryLogEntity> wrapper = new LambdaQueryWrapper<NotifyDeliveryLogEntity>()
            .eq(NotifyDeliveryLogEntity::getChannelType, "ZHAOHU")
            .like(StringUtils.hasText(request.getTaskNo()), NotifyDeliveryLogEntity::getTaskNo, request.getTaskNo())
            .eq(StringUtils.hasText(request.getEventType()), NotifyDeliveryLogEntity::getEventType, request.getEventType())
            .eq(StringUtils.hasText(request.getStatus()), NotifyDeliveryLogEntity::getStatus, request.getStatus())
            .orderByDesc(NotifyDeliveryLogEntity::getCreateTime);
        Page<NotifyDeliveryLogEntity> page = notifyDeliveryLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<NotifyDeliveryLogResponse> records = page.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), pageNo, pageSize);
    }

    private NotifyDeliveryLogResponse toResponse(NotifyDeliveryLogEntity entity) {
        NotifyDeliveryLogResponse response = new NotifyDeliveryLogResponse();
        response.setId(entity.getId());
        response.setConfigId(entity.getConfigId());
        response.setTaskId(entity.getTaskId());
        response.setTaskNo(entity.getTaskNo());
        response.setEventType(entity.getEventType());
        response.setChannelType(entity.getChannelType());
        response.setWebhookUrl(entity.getWebhookUrl());
        response.setRequestContent(entity.getRequestContent());
        response.setResponseContent(entity.getResponseContent());
        response.setStatus(entity.getStatus());
        response.setRetryCount(entity.getRetryCount());
        response.setNextRetryTime(entity.getNextRetryTime());
        response.setLastError(entity.getLastError());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }
}
