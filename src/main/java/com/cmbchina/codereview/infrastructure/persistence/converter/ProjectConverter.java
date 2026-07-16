package com.cmbchina.codereview.infrastructure.persistence.converter;

import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.persistence.entity.ProjectEntity;

public final class ProjectConverter {

    private ProjectConverter() {
    }

    public static Project toDomain(ProjectEntity entity) {
        if (entity == null) {
            return null;
        }
        Project project = new Project();
        project.setId(entity.getId());
        project.setProjectName(entity.getProjectName());
        project.setProjectType(entity.getProjectType());
        project.setRepoUrl(entity.getRepoUrl());
        project.setProjectToken(entity.getProjectToken());
        project.setUseDefaultToken(entity.getUseDefaultToken());
        project.setDefaultBranch(entity.getDefaultBranch());
        project.setOwnerName(entity.getOwnerName());
        project.setReviewDays(entity.getReviewDays());
        project.setScheduleCron(entity.getScheduleCron());
        project.setScheduleEnabled(entity.getScheduleEnabled());
        project.setNotifyEnabled(entity.getNotifyEnabled());
        project.setNotifyWebhookUrl(entity.getNotifyWebhookUrl());
        project.setNotifyExtraParams(entity.getNotifyExtraParams());
        project.setStatus(entity.getStatus());
        project.setRemark(entity.getRemark());
        return project;
    }

    public static ProjectEntity toEntity(Project project) {
        if (project == null) {
            return null;
        }
        ProjectEntity entity = new ProjectEntity();
        entity.setId(project.getId());
        entity.setProjectName(project.getProjectName());
        entity.setProjectType(project.getProjectType());
        entity.setRepoUrl(project.getRepoUrl());
        entity.setProjectToken(project.getProjectToken());
        entity.setUseDefaultToken(project.getUseDefaultToken());
        entity.setDefaultBranch(project.getDefaultBranch());
        entity.setOwnerName(project.getOwnerName());
        entity.setReviewDays(project.getReviewDays());
        entity.setScheduleCron(project.getScheduleCron());
        entity.setScheduleEnabled(project.getScheduleEnabled());
        entity.setNotifyEnabled(project.getNotifyEnabled());
        entity.setNotifyWebhookUrl(project.getNotifyWebhookUrl());
        entity.setNotifyExtraParams(project.getNotifyExtraParams());
        entity.setStatus(project.getStatus());
        entity.setRemark(project.getRemark());
        return entity;
    }
}
