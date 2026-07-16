package com.cmbchina.codereview.interfaces.dto.request;

import java.time.LocalDateTime;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ManualReviewStartRequest {

    @NotNull(message = "不能为空")
    private Long projectId;

    private String branch;

    private String reviewMode;

    private String baseRef;

    private String targetRef;

    private String commitRef;

    private String scanPath;

    private String scanExclude;

    private Boolean scanNoPlan;

    private Long maxTokensBudget;

    private String background;

    private Boolean sendNotification;

    private LocalDateTime reviewStartTime;

    private LocalDateTime reviewEndTime;
}
