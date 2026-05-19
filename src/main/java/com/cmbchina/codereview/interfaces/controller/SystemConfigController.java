package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.SystemConfigAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.infrastructure.ai.DeepSeekProperties;
import com.cmbchina.codereview.interfaces.dto.request.DefaultTokenUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.request.DeepSeekConfigUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.ConfigValidationResponse;
import com.cmbchina.codereview.interfaces.dto.response.DefaultTokenResponse;
import com.cmbchina.codereview.interfaces.dto.response.DeepSeekConfigResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController {

    private final SystemConfigAppService systemConfigAppService;

    private final DeepSeekProperties deepSeekProperties;

    public SystemConfigController(SystemConfigAppService systemConfigAppService,
                                  DeepSeekProperties deepSeekProperties) {
        this.systemConfigAppService = systemConfigAppService;
        this.deepSeekProperties = deepSeekProperties;
    }

    @PostMapping("/default-gitee-token/detail")
    public ApiResponse<DefaultTokenResponse> defaultGiteeTokenDetail() {
        return ApiResponse.success(systemConfigAppService.getDefaultGiteeTokenDetail());
    }

    @PostMapping("/default-gitee-token/update")
    public ApiResponse<Void> updateDefaultGiteeToken(@Valid @RequestBody DefaultTokenUpdateRequest request) {
        systemConfigAppService.updateDefaultGiteeToken(request);
        return ApiResponse.success();
    }

    @PostMapping("/default-gitee-token/validate")
    public ApiResponse<ConfigValidationResponse> validateDefaultGiteeToken() {
        return ApiResponse.success(systemConfigAppService.validateDefaultGiteeToken());
    }

    @PostMapping("/deepseek/detail")
    public ApiResponse<DeepSeekConfigResponse> deepSeekConfigDetail() {
        return ApiResponse.success(systemConfigAppService.getDeepSeekConfigDetail(
            deepSeekProperties.getApiKey(),
            deepSeekProperties.getUrl(),
            deepSeekProperties.getModel()
        ));
    }

    @PostMapping("/deepseek/update")
    public ApiResponse<Void> updateDeepSeekConfig(@Valid @RequestBody DeepSeekConfigUpdateRequest request) {
        systemConfigAppService.updateDeepSeekConfig(request);
        return ApiResponse.success();
    }

    @PostMapping("/deepseek/validate")
    public ApiResponse<ConfigValidationResponse> validateDeepSeekConfig() {
        return ApiResponse.success(systemConfigAppService.validateDeepSeekConfig(
            deepSeekProperties.getApiKey(),
            deepSeekProperties.getUrl(),
            deepSeekProperties.getModel()
        ));
    }
}
