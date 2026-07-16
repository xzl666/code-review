package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.interfaces.dto.request.ManualReviewStartRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ProjectScheduleReviewService {

    private static final ZoneId REVIEW_ZONE = ZoneId.of("Asia/Shanghai");

    private final ProjectRepository projectRepository;

    private final ReviewTaskAppService reviewTaskAppService;

    public ProjectScheduleReviewService(ProjectRepository projectRepository, ReviewTaskAppService reviewTaskAppService) {
        this.projectRepository = projectRepository;
        this.reviewTaskAppService = reviewTaskAppService;
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Shanghai")
    public void scheduleReviewTasks() {
        LocalDate yesterday = LocalDate.now(REVIEW_ZONE).minusDays(1);
        LocalDateTime startTime = yesterday.atStartOfDay();
        LocalDateTime endTime = yesterday.plusDays(1).atStartOfDay();
        for (Project project : projectRepository.listEnabled()) {
            startScheduledReview(project, startTime, endTime);
        }
    }

    private void startScheduledReview(Project project, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            ManualReviewStartRequest request = new ManualReviewStartRequest();
            request.setProjectId(project.getId());
            request.setBranch(project.getDefaultBranch());
            request.setReviewMode("RANGE");
            request.setSendNotification(true);
            request.setReviewStartTime(startTime);
            request.setReviewEndTime(endTime);
            reviewTaskAppService.scheduledStart(request);
        } catch (Exception ignored) {
            // One project must not prevent the remaining daily reviews from being submitted.
        }
    }
}
