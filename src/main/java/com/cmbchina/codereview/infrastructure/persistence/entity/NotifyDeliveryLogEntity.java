package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_notify_delivery_log")
public class NotifyDeliveryLogEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
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
}
