package com.cmbchina.codereview.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cmbchina.codereview.infrastructure.notification.ZhaohuProperties;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.NotifyDeliveryLogMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

class ZhaohuNotificationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsDailyReviewCardWithProjectAndReportUrl() throws Exception {
        ZhaohuProperties properties = new ZhaohuProperties();
        properties.setRobotId("robot-1");
        properties.setToId("user-1");
        ZhaohuNotificationService service = new ZhaohuNotificationService(
            properties, mock(NotifyDeliveryLogMapper.class), objectMapper,
            mock(ReviewIssueMapper.class), mock(JdbcTemplate.class), mock(SystemUserAppService.class));
        ReflectionTestUtils.setField(service, "appBaseUrl", "http://localhost:5173/");
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(88L);
        task.setProjectName("支付服务");
        task.setReviewBranch("master");
        task.setStatus("SUCCESS");
        task.setIssueCount(3);

        JsonNode body = objectMapper.readTree(service.cardBody(task));

        assertEquals("robot-1", body.path("fromId").asText());
        assertEquals("user-1", body.path("toId").asText());
        assertTrue(body.path("content").get(1).path("content").asText().contains("支付服务"));
        assertTrue(body.path("content").get(1).path("content").asText()
            .contains("http://localhost:5173/issues?taskId=88&userId=user-1"));
    }
}
