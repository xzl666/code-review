package com.cmbchina.codereview.infrastructure.git;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class GitDiffService {

    private final LocalRepositoryManager localRepositoryManager;

    public GitDiffService(LocalRepositoryManager localRepositoryManager) {
        this.localRepositoryManager = localRepositoryManager;
    }

    public GitDiffSummary summarize(Path repoDir, Integer reviewDays) {
        int days = reviewDays == null || reviewDays < 1 ? 7 : reviewDays;
        String since = days + " days ago";
        String commitOutput = localRepositoryManager.run(repoDir, "git", "log", "--since=" + since, "--pretty=format:%H").getStdout();
        List<String> commits = lines(commitOutput);

        String diffBase = commits.isEmpty() ? "HEAD~1" : diffBase(repoDir, commits.get(commits.size() - 1));
        String nameOutput = localRepositoryManager.run(repoDir, "git", "diff", "--name-only", diffBase, "HEAD").getStdout();
        List<String> files = lines(nameOutput);
        String diffContent = localRepositoryManager.run(repoDir, "git", "diff", "--no-ext-diff", "--unified=80", diffBase, "HEAD").getStdout();

        GitDiffSummary summary = new GitDiffSummary();
        summary.setCommitCount(commits.size());
        summary.setDiffFileCount(files.size());
        summary.setFilePaths(files);
        summary.setDiffContent(limit(diffContent, 200000));
        return summary;
    }

    private List<String> lines(String value) {
        return Arrays.stream(value == null ? new String[0] : value.split("\\R"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .collect(Collectors.toList());
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    private String diffBase(Path repoDir, String oldestCommit) {
        String parents = localRepositoryManager.run(repoDir, "git", "rev-list", "--parents", "-n", "1", oldestCommit).getStdout();
        List<String> parts = Arrays.stream(parents.trim().split("\\s+"))
            .filter(part -> !part.isEmpty())
            .collect(Collectors.toList());
        return parts.size() > 1 ? oldestCommit + "^" : oldestCommit;
    }
}
