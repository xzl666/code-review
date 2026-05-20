package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.interfaces.dto.request.ManualReviewStartRequest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProjectScheduleReviewService {

    private final ProjectRepository projectRepository;

    private final ReviewTaskAppService reviewTaskAppService;

    private final Map<Long, LocalDateTime> lastFireTimes = new ConcurrentHashMap<>();

    public ProjectScheduleReviewService(ProjectRepository projectRepository, ReviewTaskAppService reviewTaskAppService) {
        this.projectRepository = projectRepository;
        this.reviewTaskAppService = reviewTaskAppService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void scheduleReviewTasks() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        int firedCount = 0;
        for (Project project : projectRepository.listScheduledEnabled()) {
            if (shouldFire(project, now)) {
                stagger(firedCount);
                startScheduledReview(project);
                lastFireTimes.put(project.getId(), now);
                firedCount++;
            }
        }
    }

    private boolean shouldFire(Project project, LocalDateTime now) {
        if (project.getId() == null || !StringUtils.hasText(project.getScheduleCron())
            || !CronExpression.isValidExpression(project.getScheduleCron())) {
            return false;
        }
        LocalDateTime lastFireTime = lastFireTimes.getOrDefault(project.getId(), now.minusMinutes(1));
        LocalDateTime nextFireTime = CronExpression.parse(project.getScheduleCron()).next(lastFireTime);
        return nextFireTime != null && !nextFireTime.isAfter(now);
    }

    private void startScheduledReview(Project project) {
        try {
            ManualReviewStartRequest request = new ManualReviewStartRequest();
            request.setProjectId(project.getId());
            request.setBranch(project.getDefaultBranch());
            request.setReviewDays(project.getReviewDays());
            reviewTaskAppService.scheduledStart(request);
        } catch (Exception exception) {
            // Keep scheduler resilient; project-level validation errors should not stop other projects.
        }
    }

    private void stagger(int firedCount) {
        if (firedCount <= 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(firedCount, 20) * 500L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
