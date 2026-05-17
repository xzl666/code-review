package com.cmbchina.codereview.interfaces.dto.request;

import java.util.Map;
import lombok.Data;

@Data
public class NotifyTemplatePreviewRequest {

    private Long templateId;

    private String templateContent;

    private Map<String, Object> variables;
}
