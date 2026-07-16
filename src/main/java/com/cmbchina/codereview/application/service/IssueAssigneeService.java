package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.infrastructure.git.GitDiffSummary;
import com.cmbchina.codereview.infrastructure.git.LocalRepositoryManager;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.interfaces.dto.response.SystemUserResponse;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IssueAssigneeService {

    private final LocalRepositoryManager repositoryManager;
    private final SystemUserAppService userService;

    public IssueAssigneeService(LocalRepositoryManager repositoryManager, SystemUserAppService userService) {
        this.repositoryManager = repositoryManager;
        this.userService = userService;
    }

    public void assign(Path repoDir, GitDiffSummary summary, List<ReviewIssueEntity> issues) {
        List<SystemUserResponse> users = userService.list();
        String ref = StringUtils.hasText(summary.getHeadRef()) ? summary.getHeadRef() : "HEAD";
        for (ReviewIssueEntity issue : issues) {
            String author = blameAuthor(repoDir, ref, issue);
            issue.setCommitAuthor(author);
            if (!StringUtils.hasText(author)) continue;
            String normalized = author.replaceAll("\\s+", "").toLowerCase();
            for (SystemUserResponse user : users) {
                if (normalized.contains(user.getEmployeeId().toLowerCase())
                    || normalized.contains(user.getUserName().replaceAll("\\s+", "").toLowerCase())) {
                    issue.setAssigneeUserId(user.getUserId());
                    issue.setAssigneeName(user.getUserName());
                    issue.setAssigneeEmployeeId(user.getEmployeeId());
                    break;
                }
            }
        }
    }

    private String blameAuthor(Path repoDir, String ref, ReviewIssueEntity issue) {
        if (!StringUtils.hasText(issue.getFilePath()) || issue.getStartLine() == null || issue.getStartLine() < 1) return null;
        try {
            String output = repositoryManager.run(repoDir, "git", "blame", "--line-porcelain", ref,
                "-L", issue.getStartLine() + "," + issue.getStartLine(), "--", issue.getFilePath()).getStdout();
            String name = null;
            String mail = null;
            for (String line : output.split("\\R")) {
                if (line.startsWith("author ")) name = line.substring(7).trim();
                if (line.startsWith("author-mail ")) mail = line.substring(12).replace("<", "").replace(">", "").trim();
            }
            return StringUtils.hasText(mail) ? name + " " + mail : name;
        } catch (Exception ignored) {
            return null;
        }
    }
}
