package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.infrastructure.git.GitRepositoryProbe;
import com.cmbchina.codereview.infrastructure.git.LocalRepositoryManager;
import com.cmbchina.codereview.interfaces.dto.request.ProjectCommitListRequest;
import com.cmbchina.codereview.interfaces.dto.request.RepoConnectionTestRequest;
import com.cmbchina.codereview.interfaces.dto.request.ProjectCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.ProjectPageRequest;
import com.cmbchina.codereview.interfaces.dto.request.ProjectUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.ImportProjectResponse;
import com.cmbchina.codereview.interfaces.dto.response.ProjectResponse;
import com.cmbchina.codereview.interfaces.dto.response.ProjectCommitResponse;
import com.cmbchina.codereview.interfaces.dto.response.RepoConnectionTestResponse;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import com.cmbchina.codereview.interfaces.dto.response.SystemUserResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectAppService {

    private static final String DEFAULT_BRANCH = "master";

    private static final String DEFAULT_SCHEDULE_CRON = "0 0 7 * * *";

    private final ProjectRepository projectRepository;

    private final GitRepositoryProbe gitRepositoryProbe;

    private final LocalRepositoryManager localRepositoryManager;

    private final SystemUserAppService systemUserAppService;

    public ProjectAppService(ProjectRepository projectRepository,
                             GitRepositoryProbe gitRepositoryProbe,
                             LocalRepositoryManager localRepositoryManager,
                             SystemUserAppService systemUserAppService) {
        this.projectRepository = projectRepository;
        this.gitRepositoryProbe = gitRepositoryProbe;
        this.localRepositoryManager = localRepositoryManager;
        this.systemUserAppService = systemUserAppService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ProjectCreateRequest request) {
        Integer scheduleEnabled = request.getScheduleEnabled() == null ? 1 : request.getScheduleEnabled();
        String scheduleCron = defaultIfBlank(request.getScheduleCron(), DEFAULT_SCHEDULE_CRON);
        validateSchedule(scheduleEnabled, scheduleCron);
        validateNotifyExtraParams(request.getNotifyExtraParams());
        validateRepository(request.getRepoUrl(), defaultIfBlank(request.getDefaultBranch(), DEFAULT_BRANCH));
        Project project = new Project();
        project.setProjectName(request.getProjectName());
        project.setProjectType(request.getProjectType());
        project.setRepoUrl(request.getRepoUrl());
        project.setProjectToken(null);
        project.setUseDefaultToken(0);
        project.setDefaultBranch(defaultIfBlank(request.getDefaultBranch(), DEFAULT_BRANCH));
        project.setOwnerName(request.getOwnerName());
        project.setOwnerUserIds(request.getOwnerUserIds());
        project.setReviewDays(0);
        project.setScheduleCron(scheduleCron);
        project.setScheduleEnabled(scheduleEnabled);
        project.setNotifyEnabled(request.getNotifyEnabled() == null ? 1 : request.getNotifyEnabled());
        project.setNotifyWebhookUrl(request.getNotifyWebhookUrl());
        project.setNotifyExtraParams(request.getNotifyExtraParams());
        project.setStatus(BaseStatus.ENABLED.getValue());
        project.setRemark(request.getRemark());
        return projectRepository.save(project);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ProjectUpdateRequest request) {
        Project existing = ensureExists(request.getId());
        validateSchedule(request.getScheduleEnabled(), request.getScheduleCron());
        validateNotifyExtraParams(request.getNotifyExtraParams());
        validateRepository(request.getRepoUrl(), defaultIfBlank(request.getDefaultBranch(), DEFAULT_BRANCH));
        Project project = new Project();
        project.setId(request.getId());
        project.setProjectName(request.getProjectName());
        project.setProjectType(request.getProjectType());
        project.setRepoUrl(request.getRepoUrl());
        project.setProjectToken(null);
        project.setUseDefaultToken(0);
        project.setDefaultBranch(request.getDefaultBranch());
        project.setOwnerName(request.getOwnerName());
        project.setOwnerUserIds(request.getOwnerUserIds());
        project.setReviewDays(0);
        project.setScheduleCron(request.getScheduleCron());
        project.setScheduleEnabled(request.getScheduleEnabled() == null ? 0 : request.getScheduleEnabled());
        project.setNotifyEnabled(request.getNotifyEnabled() == null ? 1 : request.getNotifyEnabled());
        project.setNotifyWebhookUrl(request.getNotifyWebhookUrl());
        project.setNotifyExtraParams(request.getNotifyExtraParams());
        project.setStatus(request.getStatus());
        project.setRemark(request.getRemark());
        projectRepository.update(project);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensureExists(id);
        projectRepository.logicalDelete(id);
    }

    public ProjectResponse detail(Long id) {
        return toResponse(ensureExists(id));
    }

    public PageResponse<ProjectResponse> page(ProjectPageRequest request) {
        long pageNo = request.getPageNo() == null ? 1L : request.getPageNo();
        long pageSize = request.getPageSize() == null ? 10L : request.getPageSize();
        PageResponse<Project> projectPage = projectRepository.page(
            request.getProjectName(),
            request.getProjectType(),
            request.getStatus(),
            pageNo,
            pageSize
        );
        List<ProjectResponse> records = projectPage.getRecords().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
        return new PageResponse<>(records, projectPage.getTotal(), pageNo, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        ensureExists(id);
        projectRepository.updateStatus(id, BaseStatus.ENABLED.getValue());
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        ensureExists(id);
        projectRepository.updateStatus(id, BaseStatus.DISABLED.getValue());
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportProjectResponse importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "导入文件不能为空");
        }
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 .xlsx 文件");
        }
        ImportProjectResponse response = new ImportProjectResponse();
        DataFormatter formatter = new DataFormatter();
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            validateImportHeader(sheet.getRow(0), formatter);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                try {
                    Project project = parseProject(row, formatter);
                    Project existing = projectRepository.findByNameAndRepoUrl(project.getProjectName(), project.getRepoUrl());
                    if (existing == null) {
                        projectRepository.save(project);
                    } else {
                        project.setId(existing.getId());
                        projectRepository.update(project);
                    }
                    response.addSuccess();
                } catch (Exception exception) {
                    response.addFailure("第 " + (i + 1) + " 行导入失败：" + exception.getMessage());
                }
            }
            return response;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Excel 导入失败：" + exception.getMessage());
        }
    }

    public RepoConnectionTestResponse testRepoConnection(RepoConnectionTestRequest request) {
        String repoUrl = request.getRepoUrl();
        String branch = defaultIfBlank(request.getBranch(), DEFAULT_BRANCH);
        if (request.getProjectId() != null) {
            Project project = ensureExists(request.getProjectId());
            repoUrl = project.getRepoUrl();
            branch = defaultIfBlank(request.getBranch(), project.getDefaultBranch());
        }
        int timeoutSeconds = request.getTimeoutSeconds() == null ? 20 : request.getTimeoutSeconds();
        return gitRepositoryProbe.testConnection(repoUrl, branch, timeoutSeconds);
    }

    public byte[] importTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("项目导入");
            Row header = sheet.createRow(0);
            String[] titles = {"项目名称", "仓库地址", "项目类型(前端/后端)", "检视分支", "负责人名字(多个逗号分隔)"};
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
                sheet.setColumnWidth(i, i == 1 ? 12000 : 6000);
            }
            Row example = sheet.createRow(1);
            String[] values = {"示例项目", "git@gitee.com:team/example.git", "后端", "dev", "徐梓琅,何国庆"};
            for (int i = 0; i < values.length; i++) example.createCell(i).setCellValue(values[i]);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "生成导入模板失败：" + exception.getMessage());
        }
    }

    public List<ProjectCommitResponse> listCommits(ProjectCommitListRequest request) {
        Project project = ensureExists(request.getProjectId());
        String branch = defaultIfBlank(request.getBranch(), project.getDefaultBranch());
        Path repoDir = localRepositoryManager.prepare(project, branch);
        int limit = request.getLimit() == null ? 100 : Math.min(request.getLimit(), 200);
        String output = localRepositoryManager.run(repoDir, "git", "log", branch, "-n", String.valueOf(limit),
            "--date=iso-strict", "--pretty=format:%H%x1f%h%x1f%an%x1f%aI%x1f%P%x1f%s%x1e").getStdout();
        return parseCommits(output);
    }

    private List<ProjectCommitResponse> parseCommits(String output) {
        if (!StringUtils.hasText(output)) {
            return Collections.emptyList();
        }
        List<ProjectCommitResponse> commits = new ArrayList<>();
        for (String record : output.split("\u001e")) {
            String normalized = record.replaceFirst("^[\\r\\n]+", "");
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            String[] fields = normalized.split("\u001f", -1);
            if (fields.length < 6) {
                continue;
            }
            ProjectCommitResponse response = new ProjectCommitResponse();
            response.setHash(fields[0].trim());
            response.setShortHash(fields[1].trim());
            response.setAuthor(fields[2].trim());
            response.setCommitTime(fields[3].trim());
            response.setParentHashes(StringUtils.hasText(fields[4])
                ? Arrays.asList(fields[4].trim().split("\\s+")) : Collections.emptyList());
            response.setSubject(fields[5].trim());
            commits.add(response);
        }
        return commits;
    }

    private Project ensureExists(Long id) {
        Project project = projectRepository.findById(id);
        if (project == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        return project;
    }

    private ProjectResponse toResponse(Project project) {
        ProjectResponse response = new ProjectResponse();
        response.setId(project.getId());
        response.setProjectName(project.getProjectName());
        response.setProjectType(project.getProjectType());
        response.setRepoUrl(project.getRepoUrl());
        response.setUseDefaultToken(project.getUseDefaultToken());
        response.setDefaultBranch(project.getDefaultBranch());
        response.setOwnerName(project.getOwnerName());
        response.setOwnerUserIds(project.getOwnerUserIds());
        response.setOwners(systemUserAppService.findByUserIds(project.getOwnerUserIds()));
        response.setScheduleCron(project.getScheduleCron());
        response.setScheduleEnabled(project.getScheduleEnabled());
        response.setNotifyEnabled(project.getNotifyEnabled() == null ? 1 : project.getNotifyEnabled());
        response.setNotifyWebhookUrl(project.getNotifyWebhookUrl());
        response.setNotifyExtraParams(project.getNotifyExtraParams());
        response.setStatus(project.getStatus());
        response.setRemark(project.getRemark());
        return response;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    private Project parseProject(Row row, DataFormatter formatter) {
        String projectName = cell(row, 0, formatter);
        String repoUrl = cell(row, 1, formatter);
        String projectType = normalizeProjectType(cell(row, 2, formatter));
        if (!StringUtils.hasText(projectName)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "项目名称不能为空");
        }
        if (!StringUtils.hasText(projectType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "项目类型不能为空");
        }
        if (!StringUtils.hasText(repoUrl)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仓库地址不能为空");
        }
        Project project = new Project();
        project.setProjectName(projectName);
        project.setProjectType(projectType);
        project.setRepoUrl(repoUrl);
        project.setProjectToken(null);
        project.setUseDefaultToken(0);
        project.setDefaultBranch(defaultIfBlank(cell(row, 3, formatter), "dev"));
        project.setOwnerUserIds(resolveOwnerUserIds(cell(row, 4, formatter)));
        project.setReviewDays(0);
        project.setScheduleCron(DEFAULT_SCHEDULE_CRON);
        project.setScheduleEnabled(1);
        project.setNotifyEnabled(1);
        project.setStatus(BaseStatus.ENABLED.getValue());
        project.setRemark("Excel 导入");
        return project;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < 5; i++) {
            if (StringUtils.hasText(cell(row, i, formatter))) {
                return false;
            }
        }
        return true;
    }

    private String cell(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        return cell == null ? null : formatter.formatCellValue(cell).trim();
    }

    private String normalizeProjectType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if ("后端".equals(trimmed) || "后端项目".equals(trimmed) || "BACKEND".equalsIgnoreCase(trimmed)) {
            return "BACKEND";
        }
        if ("前端".equals(trimmed) || "前端项目".equals(trimmed) || "FRONTEND".equalsIgnoreCase(trimmed)) {
            return "FRONTEND";
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "项目类型仅支持前端/后端");
    }

    private void validateImportHeader(Row header, DataFormatter formatter) {
        String[] expected = {"项目名称", "仓库地址", "项目类型", "检视分支", "负责人名字"};
        if (header == null) throw new BizException(ErrorCode.PARAM_ERROR, "Excel 表头不能为空");
        for (int i = 0; i < expected.length; i++) {
            String title = cell(header, i, formatter);
            if (!StringUtils.hasText(title) || !title.startsWith(expected[i])) {
                throw new BizException(ErrorCode.PARAM_ERROR, "第 " + (i + 1) + " 列表头必须为“" + expected[i] + "”");
            }
        }
    }

    private List<String> resolveOwnerUserIds(String ownerNames) {
        if (!StringUtils.hasText(ownerNames)) return Collections.emptyList();
        List<String> names = Arrays.stream(ownerNames.split("[,，]"))
            .map(String::trim).filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, SystemUserResponse> users = systemUserAppService.findByNames(names);
        List<String> missing = names.stream().filter(name -> !users.containsKey(name)).collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未找到负责人：" + String.join("、", missing));
        }
        return names.stream().map(name -> users.get(name).getUserId()).collect(Collectors.toList());
    }

    private void validateSchedule(Integer scheduleEnabled, String scheduleCron) {
        if (scheduleEnabled == null || scheduleEnabled == 0) {
            return;
        }
        if (!StringUtils.hasText(scheduleCron)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "启用定时检视时 Cron 表达式不能为空");
        }
        if (!CronExpression.isValidExpression(scheduleCron)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Cron 表达式格式不正确，请使用 6 位 Spring Cron，例如：0 0 9 * * *");
        }
    }

    private void validateRepository(String repoUrl, String branch) {
        RepoConnectionTestResponse response = gitRepositoryProbe.testConnection(repoUrl, branch, 15);
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仓库地址校验失败：" + (response == null ? "未知错误" : response.getMessage()));
        }
    }

    private void validateNotifyExtraParams(String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(value);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "通知额外参数必须是合法 JSON");
        }
    }
}
