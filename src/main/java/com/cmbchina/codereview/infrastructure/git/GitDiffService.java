package com.cmbchina.codereview.infrastructure.git;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
        List<String> allCommits = lines(commitOutput);
        List<String> commits = limitList(allCommits, safeLimit(properties.getMaxCommitsPerTask(), 20));
        int skippedCommitsByLimit = allCommits.size() - commits.size();
        if (commits.isEmpty()) {
            GitDiffSummary summary = new GitDiffSummary();
            summary.setCommitCount(0);
            summary.setDiffFileCount(0);
            summary.setFilePaths(java.util.Collections.emptyList());
            summary.setDiffContent("");
            summary.setFiles(java.util.Collections.emptyList());
            return summary;
        }

        String diffBase = diffBase(repoDir, commits.get(commits.size() - 1));
        DiffFileCollection fileCollection = collectChangedFiles(repoDir, commits);
        List<String> files = fileCollection.files;
        DiffContentCollection diffContent = collectDiffContent(repoDir, diffBase, "HEAD", files);
        List<String> reviewedFiles = diffContent.files.stream()
            .map(GitDiffFile::getFilePath)
            .collect(Collectors.toList());

        GitDiffSummary summary = new GitDiffSummary();
        summary.setCommitCount(commits.size());
        summary.setDiffFileCount(reviewedFiles.size());
        summary.setFilePaths(reviewedFiles);
        summary.setDiffContent(diffContent.content);
        summary.setBaseRef(diffBase);
        summary.setHeadRef("HEAD");
        summary.setFiles(diffContent.files);
        summary.setSkippedCommitCount(skippedCommitsByLimit + fileCollection.skippedCommits);
        summary.setSkippedFileCount(fileCollection.skippedFiles + diffContent.skippedFiles);
        summary.setWarnings(warnings(summary));
        return summary;
    }

    public GitDiffSummary summarizeRange(Path repoDir, String baseRef, String targetRef) {
        List<String> changedFiles = lines(localRepositoryManager.run(repoDir, "git", "diff", "--name-only",
            "--no-renames", baseRef, targetRef).getStdout());
        return summarizeRefs(repoDir, baseRef, targetRef,
            count(repoDir, "git", "rev-list", "--count", baseRef + ".." + targetRef), changedFiles);
    }

    public GitDiffSummary summarizeTimeRange(Path repoDir,
                                             String branch,
                                             LocalDateTime startTime,
                                             LocalDateTime endTime,
                                             ZoneId zoneId) {
        String since = startTime.atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String until = endTime.minusSeconds(1).atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        List<String> allCommits = lines(localRepositoryManager.run(repoDir, "git", "log", branch,
            "--since=" + since, "--until=" + until, "--pretty=format:%H").getStdout());
        List<String> commits = limitList(allCommits, safeLimit(properties.getMaxCommitsPerTask(), 20));
        if (commits.isEmpty()) {
            return emptySummary();
        }
        String baseRef = diffBase(repoDir, commits.get(commits.size() - 1));
        String headRef = commits.get(0);
        DiffFileCollection fileCollection = collectChangedFiles(repoDir, commits);
        DiffContentCollection diffContent = collectDiffContent(repoDir, baseRef, headRef, fileCollection.files);
        GitDiffSummary summary = new GitDiffSummary();
        summary.setCommitCount(commits.size());
        summary.setDiffFileCount(diffContent.files.size());
        summary.setFilePaths(diffContent.files.stream().map(GitDiffFile::getFilePath).collect(Collectors.toList()));
        summary.setDiffContent(diffContent.content);
        summary.setFiles(diffContent.files);
        summary.setBaseRef(baseRef);
        summary.setHeadRef(headRef);
        summary.setSkippedCommitCount(allCommits.size() - commits.size() + fileCollection.skippedCommits);
        summary.setSkippedFileCount(fileCollection.skippedFiles + diffContent.skippedFiles);
        summary.setWarnings(warnings(summary));
        return summary;
    }

    public GitDiffSummary summarizeCommit(Path repoDir, String commitRef) {
        String commit = localRepositoryManager.run(repoDir, "git", "rev-parse", commitRef + "^{commit}").getStdout().trim();
        String baseRef = diffBase(repoDir, commit);
        List<String> changedFiles = lines(localRepositoryManager.run(repoDir, "git", "diff", "--name-only",
            "--no-renames", baseRef, commit).getStdout());
        return summarizeRefs(repoDir, baseRef, commit, 1, changedFiles);
    }

    public GitDiffSummary summarizeWorkspace(Path repoDir) {
        List<String> changedFiles = lines(localRepositoryManager.run(repoDir, "git", "status", "--porcelain").getStdout())
            .stream()
            .map(line -> line.length() > 3 ? line.substring(3).trim() : line)
            .collect(Collectors.toList());
        GitDiffSummary summary = new GitDiffSummary();
        summary.setCommitCount(changedFiles.isEmpty() ? 0 : 1);
        summary.setDiffFileCount(changedFiles.size());
        summary.setFilePaths(changedFiles);
        return summary;
    }

    public GitDiffSummary emptySummary() {
        GitDiffSummary summary = new GitDiffSummary();
        summary.setCommitCount(0);
        summary.setDiffFileCount(0);
        summary.setFilePaths(java.util.Collections.emptyList());
        summary.setDiffContent("");
        summary.setFiles(java.util.Collections.emptyList());
        return summary;
    }

    private GitDiffSummary summarizeRefs(Path repoDir,
                                         String baseRef,
                                         String targetRef,
                                         int commitCount,
                                         List<String> changedFiles) {
        int fileLimit = safeLimit(properties.getMaxFilesPerTask(), 30);
        List<String> selected = limitList(changedFiles, fileLimit);
        DiffContentCollection diffContent = collectDiffContent(repoDir, baseRef, targetRef, selected);
        List<String> reviewedFiles = diffContent.files.stream().map(GitDiffFile::getFilePath).collect(Collectors.toList());
        GitDiffSummary summary = new GitDiffSummary();
        summary.setCommitCount(commitCount);
        summary.setDiffFileCount(reviewedFiles.size());
        summary.setFilePaths(reviewedFiles);
        summary.setDiffContent(diffContent.content);
        summary.setFiles(diffContent.files);
        summary.setBaseRef(baseRef);
        summary.setHeadRef(targetRef);
        summary.setSkippedFileCount(changedFiles.size() - selected.size() + diffContent.skippedFiles);
        summary.setWarnings(warnings(summary));
        return summary;
    }

    private int count(Path repoDir, String... command) {
        try {
            return Integer.parseInt(localRepositoryManager.run(repoDir, command).getStdout().trim());
        } catch (Exception exception) {
            return 0;
        }
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
        int perCommitLimit = safeLimit(properties.getMaxFilesPerCommit(), 80);
        int taskLimit = safeLimit(properties.getMaxFilesPerTask(), 30);
        for (String commit : commits) {
            List<String> commitFiles;
            try {
                commitFiles = changedFilesForCommit(repoDir, commit);
            } catch (Exception exception) {
                collection.skippedCommits++;
                continue;
            }
            if (commitFiles.size() > perCommitLimit) {
                collection.skippedFiles += commitFiles.size() - perCommitLimit;
                commitFiles = commitFiles.subList(0, perCommitLimit);
            }
            for (String file : commitFiles) {
                if (files.size() >= taskLimit) {
                    collection.skippedFiles++;
                    continue;
                }
                files.add(file);
            }
        }
        collection.files = new ArrayList<>(files);
        return collection;
    }

    private DiffContentCollection collectDiffContent(Path repoDir, String diffBase, String headRef, List<String> files) {
        DiffContentCollection collection = new DiffContentCollection();
        StringBuilder builder = new StringBuilder();
        int fileLimit = safeLimit(properties.getMaxDiffCharsPerFile(), 30000);
        int taskLimit = safeLimit(properties.getMaxDiffCharsPerTask(), 200000);
        for (int i = 0; i < files.size(); i++) {
            String file = files.get(i);
            String output;
            try {
                output = localRepositoryManager.run(repoDir, "git", "diff", "--no-ext-diff", "--unified=80", diffBase, headRef, "--", file).getStdout();
            } catch (Exception exception) {
                collection.skippedFiles++;
                continue;
            }
            if (output.length() > fileLimit || builder.length() + output.length() > taskLimit) {
                collection.skippedFiles++;
                continue;
            }
            collection.files.add(new GitDiffFile(file, output));
            builder.append(output).append('\n');
        }
        collection.content = builder.toString();
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
            warnings.add("为控制检视范围，已跳过 " + summary.getSkippedCommitCount() + " 个超出数量限制或读取失败的提交。");
        }
        if (summary.getSkippedFileCount() != null && summary.getSkippedFileCount() > 0) {
            warnings.add("为避免模型上下文溢出，已跳过 " + summary.getSkippedFileCount() + " 个超出数量、大小限制或读取失败的文件。");
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
        private List<GitDiffFile> files = new ArrayList<>();
        private int skippedFiles;
    }
}
