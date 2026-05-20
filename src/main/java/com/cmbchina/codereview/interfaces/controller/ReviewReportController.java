package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.ReviewReportAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.interfaces.dto.request.IdRequest;
import com.cmbchina.codereview.interfaces.dto.response.ReviewReportResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-report")
public class ReviewReportController {

    private final ReviewReportAppService reviewReportAppService;

    public ReviewReportController(ReviewReportAppService reviewReportAppService) {
        this.reviewReportAppService = reviewReportAppService;
    }

    @PostMapping("/detail-by-task")
    public ApiResponse<ReviewReportResponse> detailByTask(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(reviewReportAppService.detailByTask(request.getId()));
    }
}
