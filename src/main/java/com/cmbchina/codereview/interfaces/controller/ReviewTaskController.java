package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.ReviewTaskAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.interfaces.dto.request.IdRequest;
import com.cmbchina.codereview.interfaces.dto.request.ManualReviewStartRequest;
import com.cmbchina.codereview.interfaces.dto.request.ReviewTaskPageRequest;
import com.cmbchina.codereview.interfaces.dto.response.ReviewTaskResponse;
import com.cmbchina.codereview.interfaces.dto.response.ReviewTaskStatisticsResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-task")
public class ReviewTaskController {

    private final ReviewTaskAppService reviewTaskAppService;

    public ReviewTaskController(ReviewTaskAppService reviewTaskAppService) {
        this.reviewTaskAppService = reviewTaskAppService;
    }

    @PostMapping("/manual-start")
    public ApiResponse<ReviewTaskResponse> manualStart(@Valid @RequestBody ManualReviewStartRequest request) {
        return ApiResponse.success(reviewTaskAppService.manualStart(request));
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@Valid @RequestBody IdRequest request) {
        reviewTaskAppService.cancel(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/retry")
    public ApiResponse<Void> retry(@Valid @RequestBody IdRequest request) {
        reviewTaskAppService.retry(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/detail")
    public ApiResponse<ReviewTaskResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(reviewTaskAppService.detail(request.getId()));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<ReviewTaskResponse>> page(@Valid @RequestBody ReviewTaskPageRequest request) {
        return ApiResponse.success(reviewTaskAppService.page(request));
    }

    @PostMapping("/statistics")
    public ApiResponse<ReviewTaskStatisticsResponse> statistics() {
        return ApiResponse.success(reviewTaskAppService.statistics());
    }
}
