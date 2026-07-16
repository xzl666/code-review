package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.SystemUserAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.interfaces.dto.response.SystemUserResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-user")
public class SystemUserController {
    private final SystemUserAppService service;
    public SystemUserController(SystemUserAppService service) { this.service = service; }
    @PostMapping("/list")
    public ApiResponse<List<SystemUserResponse>> list() { return ApiResponse.success(service.list()); }
}
