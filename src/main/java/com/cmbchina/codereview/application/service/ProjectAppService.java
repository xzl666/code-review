package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.infrastructure.git.GitRepositoryProbe;
import com.cmbchina.codereview.interfaces.dto.request.RepoConnectionTestRequest;
import com.cmbchina.codereview.interfaces.dto.request.DefaultTokenUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.request.ProjectCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.ProjectPageRequest;
import com.cmbchina.codereview.interfaces.dto.request.ProjectUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.ImportProjectResponse;
import com.cmbchina.codereview.interfaces.dto.response.ProjectResponse;
import com.cmbchina.codereview.interfaces.dto.response.RepoConnectionTestResponse;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
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

    private static final int DEFAULT_REVIEW_DAYS = 7;

    private static final String DEFAULT_SCHEDULE_CRON = "0 0 7 * * *";

    private final ProjectRepository projectRepository;

    private final SystemConfigAppService systemConfigAppService;

    private final GitRepositoryProbe gitRepositoryProbe;

    public ProjectAppService(ProjectRepository projectRepository,
                             SystemConfigAppService systemConfigAppService,
                             GitRepositoryProbe gitRepositoryProbe) {
        this.projectRepository = projectRepository;
        this.systemConfigAppService = systemConfigAppService;
        this.gitRepositoryProbe = gitRepositoryProbe;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ProjectCreateRequest request) {
        Integer scheduleEnabled = request.getScheduleEnabled() == null ? 1 : request.getScheduleEnabled();
        String scheduleCron = defaultIfBlank(request.getScheduleCron(), DEFAULT_SCHEDULE_CRON);
        validateSchedule(scheduleEnabled, scheduleCron);
        validateNotifyExtraParams(request.getNotifyExtraParams());
        validateRepository(request.getRepoUrl(), defaultIfBlank(request.getDefaultBranch(), DEFAULT_BRANCH), request.getProjectToken(), request.getUseDefaultToken());
        Project project = new Project();
        project.setProjectName(request.getProjectName());
        project.setProjectCode(request.getProjectCode());
        project.setProjectType(request.getProjectType());
        project.setRepoUrl(request.getRepoUrl());
        project.setProjectToken(request.getProjectToken());
        project.setUseDefaultToken(request.getUseDefaultToken() == null ? 1 : request.getUseDefaultToken());
        project.setDefaultBranch(defaultIfBlank(request.getDefaultBranch(), DEFAULT_BRANCH));
        project.setOwnerName(request.getOwnerName());
        project.setReviewDays(request.getReviewDays() == null ? DEFAULT_REVIEW_DAYS : request.getReviewDays());
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
        String projectToken = StringUtils.hasText(request.getProjectToken()) ? request.getProjectToken() : existing.getProjectToken();
        validateRepository(request.getRepoUrl(), defaultIfBlank(request.getDefaultBranch(), DEFAULT_BRANCH), projectToken, request.getUseDefaultToken());
        Project project = new Project();
        project.setId(request.getId());
        project.setProjectName(request.getProjectName());
        project.setProjectCode(request.getProjectCode());
        project.setProjectType(request.getProjectType());
        project.setRepoUrl(request.getRepoUrl());
        project.setProjectToken(projectToken);
        project.setUseDefaultToken(request.getUseDefaultToken());
        project.setDefaultBranch(request.getDefaultBranch());
        project.setOwnerName(request.getOwnerName());
        project.setReviewDays(request.getReviewDays());
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
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                try {
                    Project project = parseProject(row, formatter);
                    String defaultToken = cell(row, 4, formatter);
                    if (StringUtils.hasText(defaultToken)) {
                        DefaultTokenUpdateRequest tokenRequest = new DefaultTokenUpdateRequest();
                        tokenRequest.setToken(defaultToken);
                        systemConfigAppService.updateDefaultGiteeToken(tokenRequest);
                    }
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
        String token = request.getProjectToken();
        Integer useDefaultToken = request.getUseDefaultToken();
        if (request.getProjectId() != null) {
            Project project = ensureExists(request.getProjectId());
            repoUrl = project.getRepoUrl();
            branch = defaultIfBlank(request.getBranch(), project.getDefaultBranch());
            token = project.getProjectToken();
            useDefaultToken = project.getUseDefaultToken();
        }
        if (!StringUtils.hasText(token) && (useDefaultToken == null || useDefaultToken == 1)) {
            token = systemConfigAppService.getDefaultGiteeToken();
        }
        int timeoutSeconds = request.getTimeoutSeconds() == null ? 20 : request.getTimeoutSeconds();
        return gitRepositoryProbe.testConnection(repoUrl, branch, token, timeoutSeconds);
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
        response.setProjectCode(project.getProjectCode());
        response.setProjectType(project.getProjectType());
        response.setRepoUrl(project.getRepoUrl());
        response.setUseDefaultToken(project.getUseDefaultToken());
        response.setDefaultBranch(project.getDefaultBranch());
        response.setOwnerName(project.getOwnerName());
        response.setReviewDays(project.getReviewDays());
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
        String projectType = normalizeProjectType(cell(row, 1, formatter));
        String repoUrl = cell(row, 2, formatter);
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
        project.setProjectCode(buildProjectCode(projectName, repoUrl));
        project.setProjectType(projectType);
        project.setRepoUrl(repoUrl);
        project.setProjectToken(cell(row, 3, formatter));
        project.setUseDefaultToken(1);
        project.setDefaultBranch(defaultIfBlank(cell(row, 5, formatter), DEFAULT_BRANCH));
        project.setOwnerName(cell(row, 6, formatter));
        project.setReviewDays(parseReviewDays(cell(row, 7, formatter)));
        project.setScheduleCron(DEFAULT_SCHEDULE_CRON);
        project.setScheduleEnabled(1);
        project.setNotifyEnabled(1);
        project.setStatus(BaseStatus.ENABLED.getValue());
        project.setRemark("Excel 导入");
        return project;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < 8; i++) {
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
        if ("后端项目".equals(trimmed) || "BACKEND".equalsIgnoreCase(trimmed)) {
            return "BACKEND";
        }
        if ("前端项目".equals(trimmed) || "FRONTEND".equalsIgnoreCase(trimmed)) {
            return "FRONTEND";
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "项目类型仅支持前端项目/后端项目");
    }

    private Integer parseReviewDays(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_REVIEW_DAYS;
        }
        try {
            int days = Integer.parseInt(value);
            if (days <= 0) {
                throw new NumberFormatException("review days must be positive");
            }
            return days;
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "最近检视天数必须为正整数");
        }
    }

    private String buildProjectCode(String projectName, String repoUrl) {
        String lastPath = repoUrl;
        int slashIndex = repoUrl.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < repoUrl.length()) {
            lastPath = repoUrl.substring(slashIndex + 1);
        }
        if (lastPath.endsWith(".git")) {
            lastPath = lastPath.substring(0, lastPath.length() - 4);
        }
        String code = lastPath.replaceAll("[^A-Za-z0-9_-]", "-").toLowerCase();
        if (StringUtils.hasText(code)) {
            return code;
        }
        return projectName.replaceAll("\\s+", "-").toLowerCase();
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

    private void validateRepository(String repoUrl, String branch, String projectToken, Integer useDefaultToken) {
        String token = projectToken;
        if (!StringUtils.hasText(token) && (useDefaultToken == null || useDefaultToken == 1)) {
            token = systemConfigAppService.getDefaultGiteeToken();
        }
        RepoConnectionTestResponse response = gitRepositoryProbe.testConnection(repoUrl, branch, token, 15);
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
