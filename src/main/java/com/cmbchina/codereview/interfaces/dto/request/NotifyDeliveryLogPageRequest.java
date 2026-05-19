package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyDeliveryLogPageRequest extends PageRequest {

    private String taskNo;

    private String eventType;

    private String status;
}
