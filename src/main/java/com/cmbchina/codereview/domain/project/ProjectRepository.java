package com.cmbchina.codereview.domain.project;

import com.cmbchina.codereview.common.response.PageResponse;
import java.util.List;

public interface ProjectRepository {

    Long save(Project project);

    void update(Project project);

    Project findById(Long id);

    Project findByNameAndRepoUrl(String projectName, String repoUrl);

    PageResponse<Project> page(String projectName, String projectType, Integer status, long pageNo, long pageSize);

    List<Project> listScheduledEnabled();

    List<Project> listEnabled();

    void logicalDelete(Long id);

    void updateStatus(Long id, Integer status);
}
