package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.infrastructure.persistence.entity.NotifyConfigEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.NotifyTemplateEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.NotifyConfigMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.NotifyTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class NotificationDispatchService {

    private static final String WEBHOOK = "WEBHOOK";

    private final NotifyConfigMapper notifyConfigMapper;

    private final NotifyTemplateMapper notifyTemplateMapper;

    private final ObjectMapper objectMapper;

    public NotificationDispatchService(NotifyConfigMapper notifyConfigMapper,
                                       NotifyTemplateMapper notifyTemplateMapper,
                                       ObjectMapper objectMapper) {
        this.notifyConfigMapper = notifyConfigMapper;
        this.notifyTemplateMapper = notifyTemplateMapper;
        this.objectMapper = objectMapper;
    }

    public void notifyTaskSuccess(ReviewTaskEntity task) {
        dispatch("TASK_SUCCESS", task);
    }

    public void notifyTaskFailed(ReviewTaskEntity task) {
        dispatch("TASK_FAILED", task);
    }

    private void dispatch(String eventType, ReviewTaskEntity task) {
        List<NotifyConfigEntity> configs = notifyConfigMapper.selectList(new LambdaQueryWrapper<NotifyConfigEntity>()
            .eq(NotifyConfigEntity::getEnabled, BaseStatus.ENABLED.getValue())
            .eq(NotifyConfigEntity::getChannelType, WEBHOOK));
        if (configs.isEmpty()) {
            return;
        }
        NotifyTemplateEntity template = findTemplate(eventType);
        String content = render(template == null ? defaultTemplate(eventType) : template.getTemplateContent(), variables(task));
        for (NotifyConfigEntity config : configs) {
            if (!StringUtils.hasText(config.getWebhookUrl())) {
                continue;
            }
            try {
                send(config.getWebhookUrl(), eventType, content, task);
            } catch (Exception ignored) {
                // Notification failure should not change review task result.
            }
        }
    }

    private NotifyTemplateEntity findTemplate(String eventType) {
        return notifyTemplateMapper.selectOne(new LambdaQueryWrapper<NotifyTemplateEntity>()
            .eq(NotifyTemplateEntity::getEnabled, BaseStatus.ENABLED.getValue())
            .eq(NotifyTemplateEntity::getChannelType, WEBHOOK)
            .eq(NotifyTemplateEntity::getEventType, eventType)
            .last("LIMIT 1"));
    }

    private void send(String webhookUrl, String eventType, String content, ReviewTaskEntity task) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", eventType);
        body.put("title", "Code Review " + ("TASK_SUCCESS".equals(eventType) ? "Success" : "Failed"));
        body.put("content", content);
        body.put("taskId", task.getId());
        body.put("taskNo", task.getTaskNo());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate().exchange(webhookUrl, HttpMethod.POST, new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
    }

    private Map<String, Object> variables(ReviewTaskEntity task) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("taskId", task.getId());
        variables.put("taskNo", task.getTaskNo());
        variables.put("projectName", task.getProjectName());
        variables.put("status", task.getStatus());
        variables.put("issueCount", task.getIssueCount());
        variables.put("blockerCount", task.getBlockerCount());
        variables.put("criticalCount", task.getCriticalCount());
        variables.put("majorCount", task.getMajorCount());
        variables.put("minorCount", task.getMinorCount());
        variables.put("infoCount", task.getInfoCount());
        variables.put("errorMessage", task.getErrorMessage());
        return variables;
    }

    private String render(String content, Map<String, Object> variables) {
        Map<String, Object> safeVariables = variables == null ? Collections.emptyMap() : variables;
        String result = content;
        for (Map.Entry<String, Object> entry : safeVariables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String defaultTemplate(String eventType) {
        if ("TASK_FAILED".equals(eventType)) {
            return "Task ${taskNo} for ${projectName} failed: ${errorMessage}";
        }
        return "Task ${taskNo} for ${projectName} completed with ${issueCount} issues.";
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
