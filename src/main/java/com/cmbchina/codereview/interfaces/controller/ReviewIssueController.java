package com.cmbchina.codereview.interfaces.controller;

import com.cmbchina.codereview.application.service.ReviewIssueAppService;
import com.cmbchina.codereview.common.response.ApiResponse;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.interfaces.dto.request.IdRequest;
import com.cmbchina.codereview.interfaces.dto.request.ReviewIssuePageRequest;
import com.cmbchina.codereview.interfaces.dto.response.ReviewIssueResponse;
import com.cmbchina.codereview.interfaces.dto.response.ReviewIssueStatisticsResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-issue")
public class ReviewIssueController {

    private final ReviewIssueAppService reviewIssueAppService;

    public ReviewIssueController(ReviewIssueAppService reviewIssueAppService) {
        this.reviewIssueAppService = reviewIssueAppService;
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<ReviewIssueResponse>> page(@Valid @RequestBody ReviewIssuePageRequest request) {
        return ApiResponse.success(reviewIssueAppService.page(request));
    }

    @PostMapping("/detail")
    public ApiResponse<ReviewIssueResponse> detail(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(reviewIssueAppService.detail(request.getId()));
    }

    @PostMapping("/ignore")
    public ApiResponse<Void> ignore(@Valid @RequestBody IdRequest request) {
        reviewIssueAppService.ignore(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/mark-fixed")
    public ApiResponse<Void> markFixed(@Valid @RequestBody IdRequest request) {
        reviewIssueAppService.markFixed(request.getId());
        return ApiResponse.success();
    }

    @PostMapping("/statistics")
    public ApiResponse<ReviewIssueStatisticsResponse> statistics(@RequestBody ReviewIssuePageRequest request) {
        return ApiResponse.success(reviewIssueAppService.statistics(request));
    }

    @PostMapping("/export")
    public ApiResponse<String> export(@RequestBody ReviewIssuePageRequest request) {
        return ApiResponse.success(reviewIssueAppService.export(request));
    }
}
