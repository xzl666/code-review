package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyTemplatePageRequest extends PageRequest {

    private String templateName;

    private String templateCode;

    private String channelType;

    private String eventType;

    private Integer enabled;
}
