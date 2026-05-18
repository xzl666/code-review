package com.cmbchina.codereview.infrastructure.git;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class GitDiffService {

    private final LocalRepositoryManager localRepositoryManager;

    private final GitDiffProperties properties;

    public GitDiffService(LocalRepositoryManager localRepositoryManager, GitDiffProperties properties) {
        this.localRepositoryManager = localRepositoryManager;
        this.properties = properties;
    }

    public GitDiffSummary summarize(Path repoDir, Integer reviewDays) {
        int days = reviewDays == null || reviewDays < 1 ? 7 : reviewDays;
        String since = days + " days ago";
        String commitOutput = localRepositoryManager.run(repoDir, "git", "log", "--since=" + since, "--pretty=format:%H").getStdout();
        List<String> commits = limitList(lines(commitOutput), safeLimit(properties.getMaxCommitsPerTask(), 20));
        if (commits.isEmpty()) {
            GitDiffSummary summary = new GitDiffSummary();
            summary.setCommitCount(0);
            summary.setDiffFileCount(0);
            summary.setFilePaths(java.util.Collections.emptyList());
            summary.setDiffContent("");
            return summary;
        }

        String diffBase = diffBase(repoDir, commits.get(commits.size() - 1));
        DiffFileCollection fileCollection = collectChangedFiles(repoDir, commits);
        List<String> files = limitList(fileCollection.files, safeLimit(properties.getMaxFilesPerTask(), 30));
        int skippedFilesByLimit = Math.max(0, fileCollection.files.size() - files.size());
        DiffContentCollection diffContent = collectDiffContent(repoDir, diffBase, files);

        GitDiffSummary summary = new GitDiffSummary();
        summary.setCommitCount(commits.size());
        summary.setDiffFileCount(files.size());
        summary.setFilePaths(files);
        summary.setDiffContent(limit(diffContent.content, safeLimit(properties.getMaxDiffCharsPerTask(), 200000)));
        summary.setSkippedCommitCount(fileCollection.skippedCommits);
        summary.setSkippedFileCount(fileCollection.skippedFiles + skippedFilesByLimit + diffContent.skippedFiles);
        summary.setWarnings(warnings(summary));
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

    private DiffFileCollection collectChangedFiles(Path repoDir, List<String> commits) {
        DiffFileCollection collection = new DiffFileCollection();
        Set<String> files = new LinkedHashSet<>();
        for (String commit : commits) {
            List<String> commitFiles;
            try {
                commitFiles = changedFilesForCommit(repoDir, commit);
            } catch (Exception exception) {
                collection.skippedCommits++;
                continue;
            }
            if (commitFiles.size() > safeLimit(properties.getMaxFilesPerCommit(), 80)) {
                collection.skippedCommits++;
                collection.skippedFiles += commitFiles.size();
                continue;
            }
            files.addAll(commitFiles);
            if (files.size() >= safeLimit(properties.getMaxFilesPerTask(), 30)) {
                break;
            }
        }
        collection.files = new ArrayList<>(files);
        return collection;
    }

    private DiffContentCollection collectDiffContent(Path repoDir, String diffBase, List<String> files) {
        DiffContentCollection collection = new DiffContentCollection();
        StringBuilder builder = new StringBuilder();
        int maxFiles = Math.min(files.size(), safeLimit(properties.getMaxFilesPerTask(), 30));
        for (int i = 0; i < maxFiles; i++) {
            String file = files.get(i);
            String output;
            try {
                output = localRepositoryManager.run(repoDir, "git", "diff", "--no-ext-diff", "--unified=80", diffBase, "HEAD", "--", file).getStdout();
            } catch (Exception exception) {
                collection.skippedFiles++;
                continue;
            }
            builder.append(limit(output, safeLimit(properties.getMaxDiffCharsPerFile(), 30000))).append('\n');
            if (builder.length() >= safeLimit(properties.getMaxDiffCharsPerTask(), 200000)) {
                collection.skippedFiles += Math.max(0, files.size() - i - 1);
                break;
            }
        }
        collection.content = limit(builder.toString(), safeLimit(properties.getMaxDiffCharsPerTask(), 200000));
        return collection;
    }

    private String diffBase(Path repoDir, String oldestCommit) {
        String parents = localRepositoryManager.run(repoDir, "git", "rev-list", "--parents", "-n", "1", oldestCommit).getStdout();
        List<String> parts = Arrays.stream(parents.trim().split("\\s+"))
            .filter(part -> !part.isEmpty())
            .collect(Collectors.toList());
        return parts.size() > 1 ? oldestCommit + "^" : oldestCommit;
    }

    private List<String> changedFilesForCommit(Path repoDir, String commit) {
        String output = localRepositoryManager.run(repoDir, "git", "show", "--pretty=format:", "--name-only", "--no-renames", commit).getStdout();
        return lines(output);
    }

    private List<String> limitList(List<String> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }
        return new ArrayList<>(values.subList(0, limit));
    }

    private int safeLimit(Integer value, int defaultValue) {
        return value == null || value < 1 ? defaultValue : value;
    }

    private List<String> warnings(GitDiffSummary summary) {
        List<String> warnings = new ArrayList<>();
        if (summary.getSkippedCommitCount() != null && summary.getSkippedCommitCount() > 0) {
            warnings.add("Skipped " + summary.getSkippedCommitCount() + " oversized or timed-out commits.");
        }
        if (summary.getSkippedFileCount() != null && summary.getSkippedFileCount() > 0) {
            warnings.add("Skipped " + summary.getSkippedFileCount() + " files due to review limits or diff timeouts.");
        }
        return warnings;
    }

    private static class DiffFileCollection {
        private List<String> files = new ArrayList<>();
        private int skippedCommits;
        private int skippedFiles;
    }

    private static class DiffContentCollection {
        private String content = "";
        private int skippedFiles;
    }
}
