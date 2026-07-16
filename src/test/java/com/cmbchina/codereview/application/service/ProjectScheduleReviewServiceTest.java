package com.cmbchina.codereview.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.interfaces.dto.request.ManualReviewStartRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProjectScheduleReviewServiceTest {

    @Test
    void submitsYesterdayForEachEnabledProjectBranch() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ReviewTaskAppService reviewTaskAppService = mock(ReviewTaskAppService.class);
        Project project = new Project();
        project.setId(7L);
        project.setDefaultBranch("release");
        when(projectRepository.listEnabled()).thenReturn(Collections.singletonList(project));
        ProjectScheduleReviewService service = new ProjectScheduleReviewService(projectRepository, reviewTaskAppService);

        service.scheduleReviewTasks();

        ArgumentCaptor<ManualReviewStartRequest> captor = ArgumentCaptor.forClass(ManualReviewStartRequest.class);
        verify(reviewTaskAppService).scheduledStart(captor.capture());
        ManualReviewStartRequest request = captor.getValue();
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        assertEquals(7L, request.getProjectId());
        assertEquals("release", request.getBranch());
        assertEquals("RANGE", request.getReviewMode());
        assertEquals(yesterday.atStartOfDay(), request.getReviewStartTime());
        assertEquals(yesterday.plusDays(1).atStartOfDay(), request.getReviewEndTime());
        assertNotNull(request.getReviewEndTime());
    }
}
