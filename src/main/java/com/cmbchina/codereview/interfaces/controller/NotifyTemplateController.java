package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.NotifyTemplateAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.interfaces.dto.request.IdRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyTemplateCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyTemplatePageRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyTemplatePreviewRequest;
import com.cmbchina.codereview.interfaces.dto.request.NotifyTemplateUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.IdResponse;
import com.cmbchina.codereview.interfaces.dto.response.NotifyTemplatePreviewResponse;
import com.cmbchina.codereview.interfaces.dto.response.NotifyTemplateResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notify-template")
public class NotifyTemplateController {

    private final NotifyTemplateAppService notifyTemplateAppService;

    public NotifyTemplateController(NotifyTemplateAppService notifyTemplateAppService) {
        this.notifyTemplateAppService = notifyTemplateAppService;
    }

    @PostMapping("/create")
    public ApiResponse<IdResponse> create(@Valid @RequestBody NotifyTemplateCreateRequest request) {
        return ApiResponse.success(new IdResponse(notifyTemplateAppService.create(request)));
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody NotifyTemplateUpdateRequest request) {
        notifyTemplateAppService.update(request);
        return ApiResponse.success();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdRequest request) {
        notifyTemplateAppService.delete(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/detail")
    public ApiResponse<NotifyTemplateResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(notifyTemplateAppService.detail(request.getId()));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<NotifyTemplateResponse>> page(@Valid @RequestBody NotifyTemplatePageRequest request) {
        return ApiResponse.success(notifyTemplateAppService.page(request));
    }

    @PostMapping("/enable")
    public ApiResponse<Void> enable(@Valid @RequestBody IdRequest request) {
        notifyTemplateAppService.enable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable(@Valid @RequestBody IdRequest request) {
        notifyTemplateAppService.disable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/preview")
    public ApiResponse<NotifyTemplatePreviewResponse> preview(@RequestBody NotifyTemplatePreviewRequest request) {
        return ApiResponse.success(notifyTemplateAppService.preview(request));
    }
}
