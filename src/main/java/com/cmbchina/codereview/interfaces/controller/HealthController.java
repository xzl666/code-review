package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.common.response.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @PostMapping("/check")
    public ApiResponse<Map<String, String>> check() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "UP");
        return ApiResponse.success(result);
    }
}
