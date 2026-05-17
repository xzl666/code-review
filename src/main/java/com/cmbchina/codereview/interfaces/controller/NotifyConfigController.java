package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.NotifyConfigAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.interfaces.dto.request.IdRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyConfigCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyConfigPageRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyConfigTestSendRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyConfigUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.IdResponse;
import com.cmbchina.codereview.interfaces.dto.response.NotifyConfigResponse;
import com.cmbchina.codereview.interfaces.dto.response.NotifyTestSendResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notify-config")
public class NotifyConfigController {

    private final NotifyConfigAppService notifyConfigAppService;

    public NotifyConfigController(NotifyConfigAppService notifyConfigAppService) {
        this.notifyConfigAppService = notifyConfigAppService;
    }

    @PostMapping("/create")
    public ApiResponse<IdResponse> create(@Valid @RequestBody NotifyConfigCreateRequest request) {
        return ApiResponse.success(new IdResponse(notifyConfigAppService.create(request)));
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody NotifyConfigUpdateRequest request) {
        notifyConfigAppService.update(request);
        return ApiResponse.success();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdRequest request) {
        notifyConfigAppService.delete(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/detail")
    public ApiResponse<NotifyConfigResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(notifyConfigAppService.detail(request.getId()));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<NotifyConfigResponse>> page(@Valid @RequestBody NotifyConfigPageRequest request) {
        return ApiResponse.success(notifyConfigAppService.page(request));
    }

    @PostMapping("/enable")
    public ApiResponse<Void> enable(@Valid @RequestBody IdRequest request) {
        notifyConfigAppService.enable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable(@Valid @RequestBody IdRequest request) {
        notifyConfigAppService.disable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/test-send")
    public ApiResponse<NotifyTestSendResponse> testSend(@RequestBody NotifyConfigTestSendRequest request) {
        return ApiResponse.success(notifyConfigAppService.testSend(request));
    }
}
