package com.cmbchina.codereview.interfaces.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class NotifyDeliveryLogResponse {

    private Long id;

    private Long configId;

    private Long taskId;

    private String taskNo;

    private String eventType;

    private String channelType;

    private String webhookUrl;

    private String requestContent;

    private String responseContent;

    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private String lastError;

    private LocalDateTime createTime;
}
