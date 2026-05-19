package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.NotifyDeliveryLogAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.interfaces.dto.request.NotifyDeliveryLogPageRequest;
import com.cmbchina.codereview.interfaces.dto.response.NotifyDeliveryLogResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notify-delivery-log")
public class NotifyDeliveryLogController {

    private final NotifyDeliveryLogAppService notifyDeliveryLogAppService;

    public NotifyDeliveryLogController(NotifyDeliveryLogAppService notifyDeliveryLogAppService) {
        this.notifyDeliveryLogAppService = notifyDeliveryLogAppService;
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<NotifyDeliveryLogResponse>> page(@Valid @RequestBody NotifyDeliveryLogPageRequest request) {
        return ApiResponse.success(notifyDeliveryLogAppService.page(request));
    }
}
