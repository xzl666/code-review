package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.AiDraftGenerationService;
import com.cmbchina.codereview.application.service.RuleAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.interfaces.dto.request.AiGenerateScriptRequest;
import com.cmbchina.codereview.interfaces.dto.request.IdRequest;
import com.cmbchina.codereview.interfaces.dto.request.RuleCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.RulePageRequest;
import com.cmbchina.codereview.interfaces.dto.request.RuleUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.AiGeneratedScriptResponse;
import com.cmbchina.codereview.interfaces.dto.response.IdResponse;
import com.cmbchina.codereview.interfaces.dto.response.RuleResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rule")
public class RuleController {

    private final RuleAppService ruleAppService;

    private final AiDraftGenerationService aiDraftGenerationService;

    public RuleController(RuleAppService ruleAppService,
                          AiDraftGenerationService aiDraftGenerationService) {
        this.ruleAppService = ruleAppService;
        this.aiDraftGenerationService = aiDraftGenerationService;
    }

    @PostMapping("/create")
    public ApiResponse<IdResponse> create(@Valid @RequestBody RuleCreateRequest request) {
        return ApiResponse.success(new IdResponse(ruleAppService.create(request)));
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody RuleUpdateRequest request) {
        ruleAppService.update(request);
        return ApiResponse.success();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdRequest request) {
        ruleAppService.delete(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/detail")
    public ApiResponse<RuleResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(ruleAppService.detail(request.getId()));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<RuleResponse>> page(@Valid @RequestBody RulePageRequest request) {
        return ApiResponse.success(ruleAppService.page(request));
    }

    @PostMapping("/enable")
    public ApiResponse<Void> enable(@Valid @RequestBody IdRequest request) {
        ruleAppService.enable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable(@Valid @RequestBody IdRequest request) {
        ruleAppService.disable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/generate-script")
    public ApiResponse<AiGeneratedScriptResponse> generateScript(@RequestBody AiGenerateScriptRequest request) {
        return ApiResponse.success(aiDraftGenerationService.generateScript(request));
    }
}
