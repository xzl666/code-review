package com.cmbchina.codereview.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.infrastructure.persistence.converter.ProjectConverter;
import com.cmbchina.codereview.infrastructure.persistence.entity.ProjectEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ProjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ProjectRepositoryImpl implements ProjectRepository {

    private final ProjectMapper projectMapper;

    public ProjectRepositoryImpl(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @Override
    public Long save(Project project) {
        ProjectEntity entity = ProjectConverter.toEntity(project);
        projectMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Project project) {
        projectMapper.updateById(ProjectConverter.toEntity(project));
    }

    @Override
    public Project findById(Long id) {
        return ProjectConverter.toDomain(projectMapper.selectById(id));
    }

    @Override
    public Project findByNameAndRepoUrl(String projectName, String repoUrl) {
        LambdaQueryWrapper<ProjectEntity> wrapper = new LambdaQueryWrapper<ProjectEntity>()
            .eq(ProjectEntity::getProjectName, projectName)
            .eq(ProjectEntity::getRepoUrl, repoUrl)
            .last("LIMIT 1");
        return ProjectConverter.toDomain(projectMapper.selectOne(wrapper));
    }

    @Override
    public PageResponse<Project> page(String projectName, String projectType, Integer status, long pageNo, long pageSize) {
        LambdaQueryWrapper<ProjectEntity> wrapper = new LambdaQueryWrapper<ProjectEntity>()
            .like(StringUtils.hasText(projectName), ProjectEntity::getProjectName, projectName)
            .eq(StringUtils.hasText(projectType), ProjectEntity::getProjectType, projectType)
            .eq(status != null, ProjectEntity::getStatus, status)
            .orderByDesc(ProjectEntity::getCreateTime);
        Page<ProjectEntity> page = projectMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<Project> records = page.getRecords().stream()
            .map(ProjectConverter::toDomain)
            .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), pageNo, pageSize);
    }

    @Override
    public void logicalDelete(Long id) {
        projectMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        LambdaUpdateWrapper<ProjectEntity> wrapper = new LambdaUpdateWrapper<ProjectEntity>()
            .eq(ProjectEntity::getId, id)
            .set(ProjectEntity::getStatus, status);
        projectMapper.update(null, wrapper);
    }
}
