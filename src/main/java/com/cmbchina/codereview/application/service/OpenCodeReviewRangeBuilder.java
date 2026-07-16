package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.infrastructure.git.GitDiffSummary;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenCodeReviewRangeBuilder {

    public ReviewRange build(Path repoDir, GitDiffSummary summary) throws Exception {
        if (summary.getFilePaths() == null || summary.getFilePaths().isEmpty()) {
            return new ReviewRange(summary.getBaseRef(), summary.getHeadRef());
        }
        Path indexFile = Files.createTempFile("ocr-selective-index-", ".tmp");
        Files.deleteIfExists(indexFile);
        try {
            Map<String, String> environment = java.util.Collections.singletonMap(
                "GIT_INDEX_FILE", indexFile.toAbsolutePath().toString());
            String baseCommit = run(repoDir, environment, "git", "rev-parse", summary.getBaseRef()).trim();
            run(repoDir, environment, "git", "read-tree", baseCommit);
            for (String filePath : summary.getFilePaths()) {
                String treeEntry = run(repoDir, environment, "git", "ls-tree", summary.getHeadRef(), "--", filePath).trim();
                if (treeEntry.isEmpty()) {
                    run(repoDir, environment, "git", "update-index", "--force-remove", "--", filePath);
                    continue;
                }
                int tab = treeEntry.indexOf('\t');
                String[] metadata = treeEntry.substring(0, tab).split("\\s+");
                run(repoDir, environment, "git", "update-index", "--add", "--cacheinfo",
                    metadata[0], metadata[2], filePath);
            }
            String tree = run(repoDir, environment, "git", "write-tree").trim();
            String syntheticHead = run(repoDir, environment, "git", "-c", "user.name=Code Review Platform",
                "-c", "user.email=code-review@localhost", "commit-tree", tree, "-p", baseCommit,
                "-m", "Selective OpenCodeReview range").trim();
            return new ReviewRange(baseCommit, syntheticHead);
        } finally {
            Files.deleteIfExists(indexFile);
        }
    }

    private String run(Path repoDir, Map<String, String> environment, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(Arrays.asList(command)));
        builder.directory(repoDir.toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread out = copy(process.getInputStream(), stdout);
        Thread err = copy(process.getErrorStream(), stderr);
        int exit = process.waitFor();
        out.join();
        err.join();
        if (exit != 0) {
            throw new IllegalStateException("构造 OpenCodeReview 检视范围失败："
                + new String(stderr.toByteArray(), StandardCharsets.UTF_8).trim());
        }
        return new String(stdout.toByteArray(), StandardCharsets.UTF_8);
    }

    private Thread copy(java.io.InputStream input, ByteArrayOutputStream output) {
        Thread thread = new Thread(() -> {
            try {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, length);
                }
            } catch (Exception ignored) {
            }
        });
        thread.start();
        return thread;
    }

    public static class ReviewRange {
        private final String baseRef;
        private final String headRef;

        public ReviewRange(String baseRef, String headRef) {
            this.baseRef = baseRef;
            this.headRef = headRef;
        }

        public String getBaseRef() {
            return baseRef;
        }

        public String getHeadRef() {
            return headRef;
        }
    }
}
