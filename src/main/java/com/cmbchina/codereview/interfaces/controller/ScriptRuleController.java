package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.ScriptRuleAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.interfaces.dto.request.IdRequest;
import com.cmbchina.codereview.interfaces.dto.request.ScriptCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.ScriptPageRequest;
import com.cmbchina.codereview.interfaces.dto.request.ScriptTestRunRequest;
import com.cmbchina.codereview.interfaces.dto.request.ScriptUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.IdResponse;
import com.cmbchina.codereview.interfaces.dto.response.ScriptResponse;
import com.cmbchina.codereview.interfaces.dto.response.ScriptTestRunResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/script")
public class ScriptRuleController {

    private final ScriptRuleAppService scriptRuleAppService;

    public ScriptRuleController(ScriptRuleAppService scriptRuleAppService) {
        this.scriptRuleAppService = scriptRuleAppService;
    }

    @PostMapping("/create")
    public ApiResponse<IdResponse> create(@Valid @RequestBody ScriptCreateRequest request) {
        return ApiResponse.success(new IdResponse(scriptRuleAppService.create(request)));
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody ScriptUpdateRequest request) {
        scriptRuleAppService.update(request);
        return ApiResponse.success();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdRequest request) {
        scriptRuleAppService.delete(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/detail")
    public ApiResponse<ScriptResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(scriptRuleAppService.detail(request.getId()));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<ScriptResponse>> page(@Valid @RequestBody ScriptPageRequest request) {
        return ApiResponse.success(scriptRuleAppService.page(request));
    }

    @PostMapping("/enable")
    public ApiResponse<Void> enable(@Valid @RequestBody IdRequest request) {
        scriptRuleAppService.enable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable(@Valid @RequestBody IdRequest request) {
        scriptRuleAppService.disable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/test-run")
    public ApiResponse<ScriptTestRunResponse> testRun(@Valid @RequestBody ScriptTestRunRequest request) {
        return ApiResponse.success(scriptRuleAppService.testRun(request));
    }
}
