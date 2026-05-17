package com.cmbchina.codereview.interfaces.dto.request;

import java.util.Map;
import lombok.Data;

@Data
public class NotifyConfigTestSendRequest {

    private Long configId;

    private String webhookUrl;

    private String secret;

    private String title;

    private String content;

    private Map<String, Object> extra;
}
