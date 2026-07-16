package com.cmbchina.codereview.infrastructure.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "code-review.zhaohu")
public class ZhaohuProperties {

    private Boolean enabled = true;

    private String apiHost = "http://gatewayoazh.cmbchina.cn";

    private String clientId;

    private String clientSecret;

    private String robotId;

    private String toId;

    private Integer tokenExpireSeconds = 86400;

    private Integer tokenBufferSeconds = 300;

    private Integer timeoutSeconds = 10;
}
