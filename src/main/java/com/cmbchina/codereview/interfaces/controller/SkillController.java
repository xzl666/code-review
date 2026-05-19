package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.AiDraftGenerationService;
import com.cmbchina.codereview.application.service.SkillAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.interfaces.dto.request.AiGenerateSkillRequest;
import com.cmbchina.codereview.interfaces.dto.request.IdRequest;
import com.cmbchina.codereview.interfaces.dto.request.SkillCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.SkillPageRequest;
import com.cmbchina.codereview.interfaces.dto.request.SkillUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.request.ValidateSchemaRequest;
import com.cmbchina.codereview.interfaces.dto.response.AiGeneratedSkillResponse;
import com.cmbchina.codereview.interfaces.dto.response.IdResponse;
import com.cmbchina.codereview.interfaces.dto.response.SchemaValidateResponse;
import com.cmbchina.codereview.interfaces.dto.response.SkillResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skill")
public class SkillController {

    private final SkillAppService skillAppService;

    private final AiDraftGenerationService aiDraftGenerationService;

    public SkillController(SkillAppService skillAppService,
                           AiDraftGenerationService aiDraftGenerationService) {
        this.skillAppService = skillAppService;
        this.aiDraftGenerationService = aiDraftGenerationService;
    }

    @PostMapping("/create")
    public ApiResponse<IdResponse> create(@Valid @RequestBody SkillCreateRequest request) {
        return ApiResponse.success(new IdResponse(skillAppService.create(request)));
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody SkillUpdateRequest request) {
        skillAppService.update(request);
        return ApiResponse.success();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdRequest request) {
        skillAppService.delete(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/detail")
    public ApiResponse<SkillResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(skillAppService.detail(request.getId()));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<SkillResponse>> page(@Valid @RequestBody SkillPageRequest request) {
        return ApiResponse.success(skillAppService.page(request));
    }

    @PostMapping("/enable")
    public ApiResponse<Void> enable(@Valid @RequestBody IdRequest request) {
        skillAppService.enable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable(@Valid @RequestBody IdRequest request) {
        skillAppService.disable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/validate-schema")
    public ApiResponse<SchemaValidateResponse> validateSchema(@Valid @RequestBody ValidateSchemaRequest request) {
        return ApiResponse.success(skillAppService.validateSchema(request));
    }

    @PostMapping("/generate")
    public ApiResponse<AiGeneratedSkillResponse> generate(@RequestBody AiGenerateSkillRequest request) {
        return ApiResponse.success(aiDraftGenerationService.generateSkill(request));
    }
}
