package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.ZhaohuNotificationService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.interfaces.dto.request.ZhaohuTestSendRequest;
import com.cmbchina.codereview.interfaces.dto.request.ZhaohuConfigUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.ZhaohuConfigResponse;
import com.cmbchina.codereview.interfaces.dto.response.ZhaohuTestSendResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zhaohu")
public class ZhaohuNotificationController {
    private final ZhaohuNotificationService service;
    public ZhaohuNotificationController(ZhaohuNotificationService service) { this.service = service; }
    @PostMapping("/config/detail")
    public ApiResponse<ZhaohuConfigResponse> configDetail() {
        return ApiResponse.success(service.getConfig());
    }
    @PostMapping("/config/update")
    public ApiResponse<Void> updateConfig(@Valid @RequestBody ZhaohuConfigUpdateRequest request) {
        service.updateConfig(request);
        return ApiResponse.success();
    }
    @PostMapping("/test-send")
    public ApiResponse<ZhaohuTestSendResponse> testSend(@Valid @RequestBody ZhaohuTestSendRequest request) {
        return ApiResponse.success(service.testSend(request));
    }
}
