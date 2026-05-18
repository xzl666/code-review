package com.cmbchina.codereview.infrastructure.git;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitDiffProperties {

    @Value("${code-review.git.max-commits-per-task:${CODE_REVIEW_GIT_MAX_COMMITS:20}}")
    private Integer maxCommitsPerTask;

    @Value("${code-review.git.max-files-per-task:${CODE_REVIEW_GIT_MAX_FILES:30}}")
    private Integer maxFilesPerTask;

    @Value("${code-review.git.max-files-per-commit:${CODE_REVIEW_GIT_MAX_FILES_PER_COMMIT:80}}")
    private Integer maxFilesPerCommit;

    @Value("${code-review.git.max-diff-chars-per-task:${CODE_REVIEW_GIT_MAX_DIFF_CHARS:200000}}")
    private Integer maxDiffCharsPerTask;

    @Value("${code-review.git.max-diff-chars-per-file:${CODE_REVIEW_GIT_MAX_FILE_DIFF_CHARS:30000}}")
    private Integer maxDiffCharsPerFile;

    public Integer getMaxCommitsPerTask() {
        return maxCommitsPerTask;
    }

    public Integer getMaxFilesPerTask() {
        return maxFilesPerTask;
    }

    public Integer getMaxFilesPerCommit() {
        return maxFilesPerCommit;
    }

    public Integer getMaxDiffCharsPerTask() {
        return maxDiffCharsPerTask;
    }

    public Integer getMaxDiffCharsPerFile() {
        return maxDiffCharsPerFile;
    }
}
