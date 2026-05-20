package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.infrastructure.persistence.entity.NotifyConfigEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.NotifyDeliveryLogEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.NotifyTemplateEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewReportEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.NotifyConfigMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.NotifyDeliveryLogMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.NotifyTemplateMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewReportMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class NotificationDispatchService {

    private static final String WEBHOOK = "WEBHOOK";

    private static final String STATUS_PENDING = "PENDING";

    private static final String STATUS_SUCCESS = "SUCCESS";

    private static final String STATUS_FAILED = "FAILED";

    private static final int MAX_RETRY_COUNT = 3;

    private final NotifyConfigMapper notifyConfigMapper;

    private final NotifyTemplateMapper notifyTemplateMapper;

    private final NotifyDeliveryLogMapper notifyDeliveryLogMapper;

    private final ReviewReportMapper reviewReportMapper;

    private final ProjectRepository projectRepository;

    private final ObjectMapper objectMapper;

    public NotificationDispatchService(NotifyConfigMapper notifyConfigMapper,
                                       NotifyTemplateMapper notifyTemplateMapper,
                                       NotifyDeliveryLogMapper notifyDeliveryLogMapper,
                                       ReviewReportMapper reviewReportMapper,
                                       ProjectRepository projectRepository,
                                       ObjectMapper objectMapper) {
        this.notifyConfigMapper = notifyConfigMapper;
        this.notifyTemplateMapper = notifyTemplateMapper;
        this.notifyDeliveryLogMapper = notifyDeliveryLogMapper;
        this.reviewReportMapper = reviewReportMapper;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    public void notifyTaskSuccess(ReviewTaskEntity task) {
        dispatch("TASK_SUCCESS", task);
    }

    public void notifyTaskFailed(ReviewTaskEntity task) {
        dispatch("TASK_FAILED", task);
    }

    private void dispatch(String eventType, ReviewTaskEntity task) {
        if (task == null) {
            return;
        }
        Project project = projectRepository.findById(task.getProjectId());
        if (project != null && project.getNotifyEnabled() != null && project.getNotifyEnabled() == 0) {
            return;
        }
        List<NotifyTarget> targets = notifyTargets(project);
        if (targets.isEmpty()) {
            return;
        }
        ReviewReportEntity report = report(task.getId());
        NotifyTemplateEntity template = findTemplate(eventType);
        Map<String, Object> variables = variables(task, project, report, eventType);
        String content = render(template == null ? defaultTemplate(eventType) : template.getTemplateContent(), variables);
        for (NotifyTarget target : targets) {
            if (!StringUtils.hasText(target.webhookUrl)) {
                continue;
            }
            NotifyDeliveryLogEntity log = null;
            try {
                log = createLog(target, eventType, content, task, variables);
                String response = send(log.getWebhookUrl(), log.getRequestContent());
                markSuccess(log.getId(), response);
            } catch (Exception exception) {
                // Notification failure should not change review task result.
                if (log != null && log.getId() != null) {
                    markFailed(log.getId(), 1, exception.getMessage());
                }
            }
        }
    }

    private List<NotifyTarget> notifyTargets(Project project) {
        List<NotifyTarget> targets = new ArrayList<>();
        if (project != null && StringUtils.hasText(project.getNotifyWebhookUrl())) {
            targets.add(new NotifyTarget(null, project.getNotifyWebhookUrl()));
            return targets;
        }
        List<NotifyConfigEntity> configs = notifyConfigMapper.selectList(new LambdaQueryWrapper<NotifyConfigEntity>()
            .eq(NotifyConfigEntity::getEnabled, BaseStatus.ENABLED.getValue())
            .eq(NotifyConfigEntity::getChannelType, WEBHOOK));
        for (NotifyConfigEntity config : configs) {
            targets.add(new NotifyTarget(config.getId(), config.getWebhookUrl()));
        }
        return targets;
    }

    @Scheduled(fixedDelay = 60000)
    public void retryFailedDeliveries() {
        List<NotifyDeliveryLogEntity> logs = notifyDeliveryLogMapper.selectList(new LambdaQueryWrapper<NotifyDeliveryLogEntity>()
            .eq(NotifyDeliveryLogEntity::getStatus, STATUS_FAILED)
            .lt(NotifyDeliveryLogEntity::getRetryCount, MAX_RETRY_COUNT)
            .le(NotifyDeliveryLogEntity::getNextRetryTime, LocalDateTime.now())
            .last("LIMIT 20"));
        for (NotifyDeliveryLogEntity log : logs) {
            try {
                String response = send(log.getWebhookUrl(), log.getRequestContent());
                markSuccess(log.getId(), response);
            } catch (Exception exception) {
                markFailed(log.getId(), safeRetryCount(log.getRetryCount()) + 1, exception.getMessage());
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

    private NotifyDeliveryLogEntity createLog(NotifyTarget target, String eventType, String content, ReviewTaskEntity task, Map<String, Object> variables) throws Exception {
        NotifyDeliveryLogEntity log = new NotifyDeliveryLogEntity();
        log.setConfigId(target.configId);
        log.setTaskId(task.getId());
        log.setTaskNo(task.getTaskNo());
        log.setEventType(eventType);
        log.setChannelType(WEBHOOK);
        log.setWebhookUrl(target.webhookUrl);
        log.setRequestContent(requestBody(eventType, content, task, variables));
        log.setStatus(STATUS_PENDING);
        log.setRetryCount(0);
        notifyDeliveryLogMapper.insert(log);
        return log;
    }

    private String requestBody(String eventType, String content, ReviewTaskEntity task, Map<String, Object> variables) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", eventType);
        body.put("account", variables.get("notifyAccount"));
        body.put("title", variables.get("notifyTitle"));
        body.put("content", content);
        body.put("summary", variables.get("notifyContent"));
        body.put("reportHtml", variables.get("reportHtml"));
        body.put("taskId", task.getId());
        body.put("taskNo", task.getTaskNo());
        body.put("projectName", task.getProjectName());
        body.put("customParams", variables.get("customParams"));
        return objectMapper.writeValueAsString(body);
    }

    private String send(String webhookUrl, String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate().exchange(webhookUrl, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);
        return "HTTP " + response.getStatusCodeValue() + (response.getBody() == null ? "" : "; " + response.getBody());
    }

    private void markSuccess(Long id, String responseContent) {
        notifyDeliveryLogMapper.update(null, new LambdaUpdateWrapper<NotifyDeliveryLogEntity>()
            .eq(NotifyDeliveryLogEntity::getId, id)
            .set(NotifyDeliveryLogEntity::getStatus, STATUS_SUCCESS)
            .set(NotifyDeliveryLogEntity::getResponseContent, limit(responseContent, 10000))
            .set(NotifyDeliveryLogEntity::getNextRetryTime, null)
            .set(NotifyDeliveryLogEntity::getLastError, null));
    }

    private void markFailed(Long id, int retryCount, String errorMessage) {
        LocalDateTime nextRetryTime = retryCount >= MAX_RETRY_COUNT ? null : LocalDateTime.now().plusMinutes(5L * retryCount);
        notifyDeliveryLogMapper.update(null, new LambdaUpdateWrapper<NotifyDeliveryLogEntity>()
            .eq(NotifyDeliveryLogEntity::getId, id)
            .set(NotifyDeliveryLogEntity::getStatus, STATUS_FAILED)
            .set(NotifyDeliveryLogEntity::getRetryCount, retryCount)
            .set(NotifyDeliveryLogEntity::getNextRetryTime, nextRetryTime)
            .set(NotifyDeliveryLogEntity::getLastError, limit(errorMessage, 4000)));
    }

    private int safeRetryCount(Integer retryCount) {
        return retryCount == null ? 0 : retryCount;
    }

    private Map<String, Object> variables(ReviewTaskEntity task, Project project, ReviewReportEntity report, String eventType) {
        Map<String, Object> variables = new LinkedHashMap<>();
        String title = "代码检视" + ("TASK_SUCCESS".equals(eventType) ? "完成" : "失败") + "：" + task.getProjectName();
        String summary = "任务 " + task.getTaskNo() + " 发现 " + task.getIssueCount() + " 个问题，有效 "
            + (report == null ? task.getIssueCount() : report.getActiveIssueCount()) + " 个，已忽略 "
            + (report == null ? 0 : report.getIgnoredIssueCount()) + " 个。";
        variables.put("notifyAccount", project == null ? "" : project.getOwnerName());
        variables.put("notifyTitle", title);
        variables.put("notifyContent", summary);
        variables.put("taskId", task.getId());
        variables.put("taskNo", task.getTaskNo());
        variables.put("projectName", task.getProjectName());
        variables.put("projectCode", project == null ? "" : project.getProjectCode());
        variables.put("ownerName", project == null ? "" : project.getOwnerName());
        variables.put("status", task.getStatus());
        variables.put("issueCount", task.getIssueCount());
        variables.put("activeIssueCount", report == null ? task.getIssueCount() : report.getActiveIssueCount());
        variables.put("ignoredIssueCount", report == null ? 0 : report.getIgnoredIssueCount());
        variables.put("blockerCount", task.getBlockerCount());
        variables.put("criticalCount", task.getCriticalCount());
        variables.put("majorCount", task.getMajorCount());
        variables.put("minorCount", task.getMinorCount());
        variables.put("infoCount", task.getInfoCount());
        variables.put("errorMessage", task.getErrorMessage());
        variables.put("reportTitle", report == null ? "" : report.getReportTitle());
        variables.put("reportHtml", report == null ? "" : report.getReportContent());
        variables.put("customParams", customParams(project));
        variables.put("notificationAccount", variables.get("notifyAccount"));
        variables.put("notificationTitle", variables.get("notifyTitle"));
        variables.put("notificationContent", variables.get("notifyContent"));
        return variables;
    }

    private String render(String content, Map<String, Object> variables) {
        Map<String, Object> safeVariables = variables == null ? Collections.emptyMap() : variables;
        String result = content;
        for (Map.Entry<String, Object> entry : safeVariables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String defaultTemplate(String eventType) {
        if ("TASK_FAILED".equals(eventType)) {
            return "${notifyTitle}\n通知账号：${notifyAccount}\n${notifyContent}\n失败原因：${errorMessage}";
        }
        return "${notifyTitle}\n通知账号：${notifyAccount}\n${notifyContent}";
    }

    private ReviewReportEntity report(Long taskId) {
        return reviewReportMapper.selectOne(new LambdaQueryWrapper<ReviewReportEntity>()
            .eq(ReviewReportEntity::getTaskId, taskId)
            .last("LIMIT 1"));
    }

    private Map<String, Object> customParams(Project project) {
        if (project == null || !StringUtils.hasText(project.getNotifyExtraParams())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(project.getNotifyExtraParams(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static class NotifyTarget {
        private final Long configId;
        private final String webhookUrl;

        private NotifyTarget(Long configId, String webhookUrl) {
            this.configId = configId;
            this.webhookUrl = webhookUrl;
        }
    }
}
