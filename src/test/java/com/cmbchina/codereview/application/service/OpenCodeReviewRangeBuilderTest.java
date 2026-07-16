package com.cmbchina.codereview.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cmbchina.codereview.infrastructure.git.GitDiffSummary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenCodeReviewRangeBuilderTest {

    @TempDir
    Path repo;

    @Test
    void createsRangeContainingOnlySelectedFiles() throws Exception {
        git("init");
        git("config", "user.name", "test");
        git("config", "user.email", "test@example.com");
        Files.write(repo.resolve("a.txt"), Arrays.asList("base-a"), StandardCharsets.UTF_8);
        Files.write(repo.resolve("b.txt"), Arrays.asList("base-b"), StandardCharsets.UTF_8);
        git("add", ".");
        git("commit", "-m", "base");
        String base = git("rev-parse", "HEAD").trim();
        Files.write(repo.resolve("a.txt"), Arrays.asList("head-a"), StandardCharsets.UTF_8);
        Files.write(repo.resolve("b.txt"), Arrays.asList("head-b"), StandardCharsets.UTF_8);
        git("add", ".");
        git("commit", "-m", "head");

        GitDiffSummary summary = new GitDiffSummary();
        summary.setBaseRef(base);
        summary.setHeadRef("HEAD");
        summary.setFilePaths(Arrays.asList("a.txt"));
        OpenCodeReviewRangeBuilder.ReviewRange range = new OpenCodeReviewRangeBuilder().build(repo, summary);

        assertEquals("a.txt", git("diff", "--name-only", range.getBaseRef(), range.getHeadRef()).trim());
        assertEquals("head-a", git("show", range.getHeadRef() + ":a.txt").trim());
        assertEquals("base-b", git("show", range.getHeadRef() + ":b.txt").trim());
    }

    private String git(String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = "git";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command).directory(repo.toFile()).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException(stderr);
        }
        return stdout;
    }
}
