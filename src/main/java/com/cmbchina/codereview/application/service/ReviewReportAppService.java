package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cmbchina.codereview.common.enums.ReviewIssueStatus;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.domain.project.ProjectRepository;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewReportEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewTaskEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewReportMapper;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewTaskMapper;
import com.cmbchina.codereview.interfaces.dto.response.ReviewReportResponse;
import com.cmbchina.codereview.interfaces.dto.response.SystemUserResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ReviewReportAppService {

    private final ReviewReportMapper reviewReportMapper;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewIssueMapper reviewIssueMapper;
    private final ProjectRepository projectRepository;
    private final ReviewIssueFingerprintService fingerprintService;
    private final SystemUserAppService systemUserAppService;

    public ReviewReportAppService(ReviewReportMapper reviewReportMapper,
                                  ReviewTaskMapper reviewTaskMapper,
                                  ReviewIssueMapper reviewIssueMapper,
                                  ProjectRepository projectRepository,
                                  ReviewIssueFingerprintService fingerprintService,
                                  SystemUserAppService systemUserAppService) {
        this.reviewReportMapper = reviewReportMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewIssueMapper = reviewIssueMapper;
        this.projectRepository = projectRepository;
        this.fingerprintService = fingerprintService;
        this.systemUserAppService = systemUserAppService;
    }

    public ReviewReportResponse detailByTask(Long taskId) {
        ReviewReportEntity report = reviewReportMapper.selectOne(new LambdaQueryWrapper<ReviewReportEntity>()
            .eq(ReviewReportEntity::getTaskId, taskId)
            .last("LIMIT 1"));
        if (report == null) {
            report = generate(taskId);
        }
        return toResponse(report);
    }

    public ReviewReportEntity generate(Long taskId) {
        ReviewTaskEntity task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "检视任务不存在");
        }
        Project project = projectRepository.findById(task.getProjectId());
        List<ReviewIssueEntity> issues = reviewIssueMapper.selectList(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getTaskId, taskId)
            .orderByDesc(ReviewIssueEntity::getSeverity)
            .orderByAsc(ReviewIssueEntity::getFilePath)
            .orderByAsc(ReviewIssueEntity::getStartLine));
        List<ReviewIssueEntity> activeIssues = issues.stream()
            .filter(issue -> !ReviewIssueStatus.IGNORED.name().equals(issue.getStatus()))
            .collect(Collectors.toList());
        List<ReviewIssueEntity> ignoredIssues = issues.stream()
            .filter(issue -> ReviewIssueStatus.IGNORED.name().equals(issue.getStatus()))
            .collect(Collectors.toList());

        ReviewReportEntity entity = existing(taskId);
        if (entity == null) {
            entity = new ReviewReportEntity();
        }
        entity.setTaskId(task.getId());
        entity.setTaskNo(task.getTaskNo());
        entity.setProjectId(task.getProjectId());
        entity.setReportTitle(reportTitle(task));
        entity.setReportContent(renderReport(task, project, activeIssues, ignoredIssues));
        entity.setActiveIssueCount(activeIssues.size());
        entity.setIgnoredIssueCount(ignoredIssues.size());
        if (entity.getId() == null) {
            reviewReportMapper.insert(entity);
        } else {
            reviewReportMapper.updateById(entity);
        }
        return entity;
    }

    public void regenerateForIssue(Long issueId) {
        ReviewIssueEntity issue = reviewIssueMapper.selectById(issueId);
        if (issue != null && issue.getTaskId() != null) {
            generate(issue.getTaskId());
        }
    }

    private ReviewReportEntity existing(Long taskId) {
        return reviewReportMapper.selectOne(new LambdaQueryWrapper<ReviewReportEntity>()
            .eq(ReviewReportEntity::getTaskId, taskId)
            .last("LIMIT 1"));
    }

    private String reportTitle(ReviewTaskEntity task) {
        return "代码检视报告 - " + task.getProjectName() + " - " + task.getTaskNo();
    }

    private String renderReport(ReviewTaskEntity task, Project project, List<ReviewIssueEntity> activeIssues, List<ReviewIssueEntity> ignoredIssues) {
        StringBuilder html = new StringBuilder();
        html.append("<article class=\"review-report\">")
            .append("<header class=\"report-hero\"><div><p>Code Review Report</p><h1>")
            .append(escape(task.getProjectName()))
            .append("</h1><span>")
            .append(escape(task.getTaskNo()))
            .append("</span></div><strong>")
            .append(escape(statusText(task.getStatus())))
            .append("</strong></header>");
        html.append("<section class=\"report-summary\">")
            .append(summaryCard("有效问题", activeIssues.size()))
            .append(summaryCard("已忽略", ignoredIssues.size()))
            .append(summaryCard("提交数", task.getCommitCount()))
            .append(summaryCard("变更文件", task.getDiffFileCount()))
            .append("</section>");
        html.append("<section class=\"report-meta\">")
            .append(meta("项目负责人", ownersText(project)))
            .append(meta("分支", task.getReviewBranch()))
            .append(meta("触发方式", triggerText(task.getTriggerType())))
            .append(meta("检视方式", reviewModeText(task.getReviewMode())))
            .append(meta("检视范围", reviewRangeText(task)))
            .append(meta("模型调用", number(task.getAiCallCount()) + " 次"))
            .append(meta("Token 消耗", number(task.getTotalTokenCount())))
            .append(meta("完成时间", task.getEndTime() == null ? "" : task.getEndTime().toString().replace('T', ' ')))
            .append("</section>");
        appendIssueSection(html, "有效问题", activeIssues, false);
        appendIssueSection(html, "已忽略问题", ignoredIssues, true);
        html.append("</article>");
        return html.toString();
    }

    private String reviewRangeText(ReviewTaskEntity task) {
        if (task.getReviewStartTime() != null && task.getReviewEndTime() != null) {
            return task.getReviewStartTime().toString().replace('T', ' ') + " 至 "
                + task.getReviewEndTime().toString().replace('T', ' ');
        }
        String mode = task.getReviewMode() == null ? "" : task.getReviewMode();
        switch (mode) {
            case "COMMIT":
                return value(task.getCommitRef(), "未记录提交版本");
            case "WORKSPACE":
                return "当前工作区变更";
            case "SCAN":
                return "全量扫描：" + value(task.getScanPath(), "项目根目录");
            case "YESTERDAY":
                return "昨天 00:00 至今天 00:00";
            case "RANGE":
            default:
                if (hasText(task.getBaseRef()) || hasText(task.getTargetRef())) {
                    return value(task.getBaseRef(), "起始版本未记录") + " 至 "
                        + value(task.getTargetRef(), value(task.getReviewBranch(), "目标版本未记录"));
                }
                return value(task.getReviewBranch(), "默认分支") + " 分支区间";
        }
    }

    private String ownersText(Project project) {
        if (project == null || project.getOwnerUserIds() == null || project.getOwnerUserIds().isEmpty()) {
            return "未配置";
        }
        List<SystemUserResponse> owners = systemUserAppService.findByUserIds(project.getOwnerUserIds());
        if (owners.isEmpty()) {
            return "未配置";
        }
        return owners.stream()
            .map(owner -> owner.getUserName() + (hasText(owner.getEmployeeId()) ? " " + owner.getEmployeeId() : ""))
            .collect(Collectors.joining("、"));
    }

    private String reviewModeText(String mode) {
        if (mode == null) return "未知";
        switch (mode) {
            case "RANGE": return "分支区间";
            case "YESTERDAY": return "昨天提交";
            case "COMMIT": return "单个提交";
            case "WORKSPACE": return "工作区";
            case "SCAN": return "全量扫描";
            default: return mode;
        }
    }

    private String triggerText(String triggerType) {
        if ("SCHEDULE".equals(triggerType)) return "定时任务";
        if ("MANUAL".equals(triggerType)) return "手动触发";
        return value(triggerType, "未知");
    }

    private String statusText(String status) {
        if ("SUCCESS".equals(status)) return "成功";
        if ("FAILED".equals(status)) return "失败";
        if ("RUNNING".equals(status)) return "执行中";
        if ("PENDING".equals(status)) return "等待执行";
        if ("CANCELED".equals(status)) return "已取消";
        return value(status, "未知");
    }

    private String value(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String number(Number value) {
        return value == null ? "0" : String.format("%,d", value.longValue());
    }

    private void appendIssueSection(StringBuilder html, String title, List<ReviewIssueEntity> issues, boolean ignored) {
        html.append("<section class=\"report-section\"><h2>").append(title).append("</h2>");
        if (issues.isEmpty()) {
            html.append("<div class=\"report-empty\">暂无").append(title).append("</div></section>");
            return;
        }
        Map<String, List<ReviewIssueEntity>> bySeverity = issues.stream().collect(Collectors.groupingBy(ReviewIssueEntity::getSeverity));
        for (String severity : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
            List<ReviewIssueEntity> group = bySeverity.get(severity);
            if (group == null || group.isEmpty()) {
                continue;
            }
            html.append("<h3>").append(severityText(severity)).append("</h3>");
            for (ReviewIssueEntity issue : group) {
                html.append("<div class=\"report-issue ").append(ignored ? "is-ignored" : "").append("\">")
                    .append("<div class=\"report-issue-head\"><strong>").append(escape(issue.getSummary())).append("</strong>")
                    .append("<span>").append(firstSeenText(issue)).append("</span></div>")
                    .append("<p>").append(escape(issue.getFilePath())).append(lineText(issue)).append("</p>")
                    .append("<div>").append(escape(issue.getDetail())).append("</div>");
                if (issue.getSuggestion() != null) {
                    html.append("<blockquote>").append(escape(issue.getSuggestion())).append("</blockquote>");
                }
                html.append("</div>");
            }
        }
        html.append("</section>");
    }

    private String severityText(String severity) {
        switch (severity) {
            case "CRITICAL": return "严重";
            case "HIGH": return "高";
            case "MEDIUM": return "中";
            case "LOW": return "低";
            default: return escape(severity);
        }
    }

    private String firstSeenText(ReviewIssueEntity issue) {
        LocalDateTime firstSeen = fingerprintService.firstSeenAt(issue);
        LocalDateTime now = issue.getCreateTime() == null ? LocalDateTime.now() : issue.getCreateTime();
        if (firstSeen == null) {
            return "首次出现";
        }
        long days = Math.max(0, Duration.between(firstSeen, now).toDays());
        if (days == 0) {
            return "今天首次出现";
        }
        return "已出现 " + days + " 天";
    }

    private String lineText(ReviewIssueEntity issue) {
        if (issue.getStartLine() == null || issue.getStartLine() < 1) {
            return "";
        }
        return " : L" + issue.getStartLine();
    }

    private String summaryCard(String label, Integer value) {
        return "<div><span>" + label + "</span><strong>" + (value == null ? 0 : value) + "</strong></div>";
    }

    private String meta(String label, String value) {
        return "<div><span>" + label + "</span><strong>" + escape(value) + "</strong></div>";
    }

    private ReviewReportResponse toResponse(ReviewReportEntity entity) {
        ReviewReportResponse response = new ReviewReportResponse();
        response.setId(entity.getId());
        response.setTaskId(entity.getTaskId());
        response.setTaskNo(entity.getTaskNo());
        response.setProjectId(entity.getProjectId());
        response.setReportTitle(entity.getReportTitle());
        response.setReportContent(entity.getReportContent());
        response.setActiveIssueCount(entity.getActiveIssueCount());
        response.setIgnoredIssueCount(entity.getIgnoredIssueCount());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
