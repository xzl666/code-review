package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cmbchina.codereview.infrastructure.notification.ZhaohuProperties;
import com.cmbchina.codereview.infrastructure.persistence.entity.NotifyDeliveryLogEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.NotifyDeliveryLogMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.cmbchina.codereview.interfaces.dto.request.ZhaohuTestSendRequest;
import com.cmbchina.codereview.interfaces.dto.response.ZhaohuTestSendResponse;
import com.cmbchina.codereview.interfaces.dto.response.SystemUserResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ZhaohuNotificationService {

    private static final String CHANNEL = "ZHAOHU";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_RETRY_COUNT = 3;

    private final ZhaohuProperties properties;
    private final NotifyDeliveryLogMapper deliveryLogMapper;
    private final ObjectMapper objectMapper;
    private final ReviewIssueMapper reviewIssueMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SystemUserAppService systemUserAppService;

    @Value("${code-review.app-base-url:${CODE_REVIEW_APP_BASE_URL:http://localhost:5173}}")
    private String appBaseUrl;

    private volatile String accessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    public ZhaohuNotificationService(ZhaohuProperties properties,
                                     NotifyDeliveryLogMapper deliveryLogMapper,
                                     ObjectMapper objectMapper,
                                     ReviewIssueMapper reviewIssueMapper,
                                     JdbcTemplate jdbcTemplate,
                                     SystemUserAppService systemUserAppService) {
        this.properties = properties;
        this.deliveryLogMapper = deliveryLogMapper;
        this.objectMapper = objectMapper;
        this.reviewIssueMapper = reviewIssueMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.systemUserAppService = systemUserAppService;
    }

    public void notifyDailyReviewCompleted(ReviewTaskEntity task) {
        if (task == null || !Integer.valueOf(1).equals(task.getNotifyEnabled()) || !Boolean.TRUE.equals(properties.getEnabled())) {
            return;
        }
        List<ReviewIssueEntity> allIssues = reviewIssueMapper.selectList(
            new LambdaQueryWrapper<ReviewIssueEntity>().eq(ReviewIssueEntity::getTaskId, task.getId()));
        Map<String, RecipientMessage> recipients = recipients(task.getProjectId(), allIssues);
        for (RecipientMessage recipient : recipients.values()) {
            NotifyDeliveryLogEntity log = null;
            try {
                log = createLog(task, cardBody(task, recipient));
                markSuccess(log.getId(), sendCard(log.getRequestContent()));
            } catch (Exception exception) {
                if (log != null && log.getId() != null) {
                    markFailed(log.getId(), 1, safeError(exception));
                }
            }
        }
    }

    public ZhaohuTestSendResponse testSend(ZhaohuTestSendRequest request) {
        ZhaohuTestSendResponse response = new ZhaohuTestSendResponse();
        Map<String, SystemUserResponse> users = systemUserAppService.findByUserIds(request.getUserIds()).stream()
            .collect(Collectors.toMap(SystemUserResponse::getUserId, user -> user));
        for (String userId : new LinkedHashSet<>(request.getUserIds())) {
            SystemUserResponse user = users.get(userId);
            if (user == null) {
                response.setFailureCount(response.getFailureCount() + 1);
                response.getFailureReasons().add(userId + "：人员不存在");
                continue;
            }
            try {
                sendCard(customCardBody(userId, request.getTitle(), request.getContent(), request.getSummary()));
                response.setSuccessCount(response.getSuccessCount() + 1);
            } catch (Exception exception) {
                response.setFailureCount(response.getFailureCount() + 1);
                response.getFailureReasons().add(user.getUserName() + "：" + safeError(exception));
            }
        }
        return response;
    }

    @Scheduled(fixedDelay = 60000)
    public void retryFailedDeliveries() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return;
        }
        List<NotifyDeliveryLogEntity> logs = deliveryLogMapper.selectList(
            new LambdaQueryWrapper<NotifyDeliveryLogEntity>()
                .eq(NotifyDeliveryLogEntity::getChannelType, CHANNEL)
                .eq(NotifyDeliveryLogEntity::getStatus, STATUS_FAILED)
                .lt(NotifyDeliveryLogEntity::getRetryCount, MAX_RETRY_COUNT)
                .le(NotifyDeliveryLogEntity::getNextRetryTime, LocalDateTime.now())
                .last("LIMIT 20"));
        for (NotifyDeliveryLogEntity log : logs) {
            try {
                markSuccess(log.getId(), sendCard(log.getRequestContent()));
            } catch (Exception exception) {
                markFailed(log.getId(), value(log.getRetryCount()) + 1, safeError(exception));
            }
        }
    }

    private NotifyDeliveryLogEntity createLog(ReviewTaskEntity task, String requestBody) {
        NotifyDeliveryLogEntity log = new NotifyDeliveryLogEntity();
        log.setTaskId(task.getId());
        log.setTaskNo(task.getTaskNo());
        log.setEventType("SUCCESS".equals(task.getStatus()) ? "DAILY_REVIEW_SUCCESS" : "DAILY_REVIEW_FAILED");
        log.setChannelType(CHANNEL);
        log.setWebhookUrl(cardUrl());
        log.setRequestContent(requestBody);
        log.setStatus(STATUS_PENDING);
        log.setRetryCount(0);
        deliveryLogMapper.insert(log);
        return log;
    }

    String cardBody(ReviewTaskEntity task) {
        RecipientMessage recipient = new RecipientMessage();
        recipient.userId = properties.getToId();
        recipient.userName = "用户";
        recipient.owner = true;
        recipient.issues = Collections.emptyList();
        return cardBody(task, recipient);
    }

    private String cardBody(ReviewTaskEntity task, RecipientMessage recipient) {
        try {
            boolean success = "SUCCESS".equals(task.getStatus());
            String title = success ? "每日代码检视完成" : "每日代码检视失败";
            String reportUrl = reportUrl(task.getId(), recipient.userId);
            String roleText = recipient.owner ? "项目负责人" : "代码提交人";
            String markdown = "**接收人员：** " + recipient.userName + "（" + roleText + "）\n\n"
                + "**项目名称：** " + task.getProjectName() + "\n\n"
                + "**检视分支：** " + task.getReviewBranch() + "\n\n"
                + "**任务状态：** " + (success ? "成功" : "失败") + "\n\n"
                + "**相关问题：** " + recipient.issues.size() + "\n\n"
                + issueMarkdown(recipient.issues)
                + "**问题详情：** [查看我的问题](" + reportUrl + ")";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fromId", properties.getRobotId());
            body.put("toId", recipient.userId);
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> titleItem = new LinkedHashMap<>();
            titleItem.put("type", "title");
            titleItem.put("content", title);
            titleItem.put("style", success ? 3 : 2);
            content.add(titleItem);
            Map<String, Object> markdownItem = new LinkedHashMap<>();
            markdownItem.put("type", "mdContainer");
            markdownItem.put("content", markdown);
            content.add(markdownItem);
            body.put("content", content);
            body.put("summary", task.getProjectName() + (success ? " 每日代码检视完成" : " 每日代码检视失败"));
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("构造招乎机器人消息失败", exception);
        }
    }

    String reportUrl(Long taskId) {
        return reportUrl(taskId, properties.getToId());
    }

    String reportUrl(Long taskId, String userId) {
        String baseUrl = StringUtils.hasText(appBaseUrl) ? appBaseUrl.trim() : "http://localhost:5173";
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/issues?taskId=" + taskId + "&userId=" + userId;
    }

    private Map<String, RecipientMessage> recipients(Long projectId, List<ReviewIssueEntity> issues) {
        List<String> ownerIds = jdbcTemplate.queryForList(
            "SELECT user_id FROM cr_project_owner WHERE project_id = ? ORDER BY id", String.class, projectId);
        Set<String> recipientIds = new LinkedHashSet<>(ownerIds);
        issues.stream().map(ReviewIssueEntity::getAssigneeUserId).filter(StringUtils::hasText).forEach(recipientIds::add);
        Map<String, SystemUserResponse> users = systemUserAppService.findByUserIds(new ArrayList<>(recipientIds)).stream()
            .collect(Collectors.toMap(SystemUserResponse::getUserId, user -> user));
        Map<String, RecipientMessage> result = new LinkedHashMap<>();
        for (String userId : recipientIds) {
            SystemUserResponse user = users.get(userId);
            if (user == null) continue;
            RecipientMessage recipient = new RecipientMessage();
            recipient.userId = userId;
            recipient.userName = user.getUserName();
            recipient.owner = ownerIds.contains(userId);
            recipient.issues = recipient.owner ? new ArrayList<>(issues) : issues.stream()
                .filter(issue -> userId.equals(issue.getAssigneeUserId())).collect(Collectors.toList());
            result.put(userId, recipient);
        }
        return result;
    }

    private String issueMarkdown(List<ReviewIssueEntity> issues) {
        if (issues.isEmpty()) return "**检视结果：** 未发现需要处理的问题\n\n";
        StringBuilder builder = new StringBuilder("**问题摘要：**\n");
        int limit = Math.min(issues.size(), 8);
        for (int index = 0; index < limit; index++) {
            ReviewIssueEntity issue = issues.get(index);
            builder.append("- [").append(issue.getSeverity()).append("] ")
                .append(issue.getSummary()).append("（").append(issue.getFilePath());
            if (issue.getStartLine() != null) builder.append(":").append(issue.getStartLine());
            builder.append("）\n");
        }
        if (issues.size() > limit) builder.append("- 另有 ").append(issues.size() - limit).append(" 个问题，请在平台查看\n");
        return builder.append("\n").toString();
    }

    private String customCardBody(String userId, String title, String contentValue, String summary) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fromId", properties.getRobotId());
            body.put("toId", userId);
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> titleItem = new LinkedHashMap<>();
            titleItem.put("type", "title"); titleItem.put("content", title); titleItem.put("style", 1);
            content.add(titleItem);
            Map<String, Object> markdownItem = new LinkedHashMap<>();
            markdownItem.put("type", "mdContainer"); markdownItem.put("content", contentValue);
            content.add(markdownItem);
            body.put("content", content);
            body.put("summary", StringUtils.hasText(summary) ? summary : title);
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("构造招乎机器人测试消息失败", exception);
        }
    }

    private static class RecipientMessage {
        private String userId;
        private String userName;
        private boolean owner;
        private List<ReviewIssueEntity> issues = Collections.emptyList();
    }

    private String sendCard(String requestBody) {
        requireConfiguration();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken());
        ResponseEntity<String> response = restTemplate().exchange(
            cardUrl(), HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);
        return "HTTP " + response.getStatusCodeValue()
            + (response.getBody() == null ? "" : "; " + response.getBody());
    }

    private synchronized String accessToken() {
        if (StringUtils.hasText(accessToken) && Instant.now().isBefore(accessTokenExpiresAt)) {
            return accessToken;
        }
        String url = UriComponentsBuilder.fromHttpUrl(apiUrl("/auth-service/oauth/token"))
            .queryParam("grant_type", "client_credentials")
            .queryParam("client_id", properties.getClientId())
            .queryParam("client_secret", properties.getClientSecret())
            .build().encode().toUriString();
        ResponseEntity<String> response = restTemplate().exchange(
            url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            String token = root.path("access_token").asText("");
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("招乎 OAuth 响应缺少 access_token");
            }
            long expiresIn = root.path("expires_in").asLong(positive(properties.getTokenExpireSeconds(), 86400));
            long buffer = Math.min(positive(properties.getTokenBufferSeconds(), 300), Math.max(0, expiresIn - 1));
            accessToken = token;
            accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(1, expiresIn - buffer));
            return accessToken;
        } catch (Exception exception) {
            throw new IllegalStateException("解析招乎 OAuth 响应失败", exception);
        }
    }

    private void requireConfiguration() {
        if (!StringUtils.hasText(properties.getApiHost()) || !StringUtils.hasText(properties.getClientId())
            || !StringUtils.hasText(properties.getClientSecret()) || !StringUtils.hasText(properties.getRobotId())) {
            throw new IllegalStateException("招乎机器人配置不完整");
        }
    }

    private String cardUrl() {
        return apiUrl("/robot-service/single-message/custom-card");
    }

    private String apiUrl(String path) {
        String host = properties.getApiHost();
        return (host.endsWith("/") ? host.substring(0, host.length() - 1) : host) + path;
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = positive(properties.getTimeoutSeconds(), 10) * 1000;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private void markSuccess(Long id, String responseContent) {
        deliveryLogMapper.update(null, new LambdaUpdateWrapper<NotifyDeliveryLogEntity>()
            .eq(NotifyDeliveryLogEntity::getId, id)
            .set(NotifyDeliveryLogEntity::getStatus, STATUS_SUCCESS)
            .set(NotifyDeliveryLogEntity::getResponseContent, limit(responseContent, 10000))
            .set(NotifyDeliveryLogEntity::getNextRetryTime, null)
            .set(NotifyDeliveryLogEntity::getLastError, null));
    }

    private void markFailed(Long id, int retryCount, String errorMessage) {
        LocalDateTime nextRetryTime = retryCount >= MAX_RETRY_COUNT ? null : LocalDateTime.now().plusMinutes(5L * retryCount);
        deliveryLogMapper.update(null, new LambdaUpdateWrapper<NotifyDeliveryLogEntity>()
            .eq(NotifyDeliveryLogEntity::getId, id)
            .set(NotifyDeliveryLogEntity::getStatus, STATUS_FAILED)
            .set(NotifyDeliveryLogEntity::getRetryCount, retryCount)
            .set(NotifyDeliveryLogEntity::getNextRetryTime, nextRetryTime)
            .set(NotifyDeliveryLogEntity::getLastError, limit(errorMessage, 4000)));
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (StringUtils.hasText(properties.getClientSecret())) {
            message = message.replace(properties.getClientSecret(), "***");
        }
        return message;
    }

    private int positive(Integer number, int fallback) {
        return number == null || number < 1 ? fallback : number;
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private String limit(String value, int length) {
        return value != null && value.length() > length ? value.substring(0, length) : value;
    }
}
