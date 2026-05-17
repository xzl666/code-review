package com.cmbchina.codereview.interfaces.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyConfigPageRequest extends PageRequest {

    private String configName;

    private String channelType;

    private Integer enabled;
}
