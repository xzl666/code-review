package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.ProjectAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.interfaces.dto.request.IdRequest;
import com.cmbchina.codereview.interfaces.dto.request.ProjectCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.ProjectPageRequest;
import com.cmbchina.codereview.interfaces.dto.request.ProjectCommitListRequest;
import com.cmbchina.codereview.interfaces.dto.request.ProjectUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.request.RepoConnectionTestRequest;
import com.cmbchina.codereview.interfaces.dto.response.IdResponse;
import com.cmbchina.codereview.interfaces.dto.response.ImportProjectResponse;
import com.cmbchina.codereview.interfaces.dto.response.ProjectResponse;
import com.cmbchina.codereview.interfaces.dto.response.ProjectCommitResponse;
import java.util.List;
import com.cmbchina.codereview.interfaces.dto.response.RepoConnectionTestResponse;
import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/project")
public class ProjectController {

    private final ProjectAppService projectAppService;

    public ProjectController(ProjectAppService projectAppService) {
        this.projectAppService = projectAppService;
    }

    @PostMapping("/create")
    public ApiResponse<IdResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        return ApiResponse.success(new IdResponse(projectAppService.create(request)));
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody ProjectUpdateRequest request) {
        projectAppService.update(request);
        return ApiResponse.success();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdRequest request) {
        projectAppService.delete(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/detail")
    public ApiResponse<ProjectResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(projectAppService.detail(request.getId()));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<ProjectResponse>> page(@Valid @RequestBody ProjectPageRequest request) {
        return ApiResponse.success(projectAppService.page(request));
    }

    @PostMapping("/enable")
    public ApiResponse<Void> enable(@Valid @RequestBody IdRequest request) {
        projectAppService.enable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable(@Valid @RequestBody IdRequest request) {
        projectAppService.disable(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/import-excel")
    public ApiResponse<ImportProjectResponse> importExcel(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(projectAppService.importExcel(file));
    }

    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws java.io.IOException {
        String fileName = URLEncoder.encode("项目导入模板.xlsx", StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.getOutputStream().write(projectAppService.importTemplate());
    }

    @PostMapping("/test-repo-connection")
    public ApiResponse<RepoConnectionTestResponse> testRepoConnection(@Valid @RequestBody RepoConnectionTestRequest request) {
        return ApiResponse.success(projectAppService.testRepoConnection(request));
    }

    @PostMapping("/commits")
    public ApiResponse<List<ProjectCommitResponse>> commits(@Valid @RequestBody ProjectCommitListRequest request) {
        return ApiResponse.success(projectAppService.listCommits(request));
    }
}
