package com.cmbchina.codereview.infrastructure.git;

import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.common.util.MaskUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalRepositoryManager {

    private static final long GIT_TIMEOUT_SECONDS = 90L;

    private final Path repoRoot = Paths.get("target", "review-repos");

    public Path prepare(Project project, String branch, String token) {
        try {
            Files.createDirectories(repoRoot);
            Path repoDir = repoRoot.resolve(safeName(project.getProjectCode() + "-" + project.getId()));
            if (Files.exists(repoDir.resolve(".git"))) {
                refresh(repoDir, project.getRepoUrl(), branch, token);
                return repoDir;
            }
            run(repoRoot, "git", "clone", "--branch", branch, "--single-branch", authUrl(project.getRepoUrl(), token), repoDir.toAbsolutePath().toString());
            return repoDir;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "prepare repository failed: " + exception.getMessage());
        }
    }

    private void refresh(Path repoDir, String repoUrl, String branch, String token) {
        try {
            run(repoDir, "git", "remote", "set-url", "origin", authUrl(repoUrl, token));
            run(repoDir, "git", "fetch", "origin", branch, "--prune", "--depth=200");
            run(repoDir, "git", "checkout", branch);
            run(repoDir, "git", "pull", "--ff-only", "origin", branch);
        } catch (BizException exception) {
            run(repoDir, "git", "checkout", branch);
        }
    }

    public GitCommandResult run(Path workingDirectory, String... command) {
        Path stdoutPath = null;
        Path stderrPath = null;
        try {
            stdoutPath = Files.createTempFile("code-review-git-stdout-", ".log");
            stderrPath = Files.createTempFile("code-review-git-stderr-", ".log");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory.toFile());
            builder.redirectOutput(stdoutPath.toFile());
            builder.redirectError(stderrPath.toFile());
            Process process = builder.start();
            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException(ErrorCode.BIZ_ERROR, "git command timeout: " + maskCommand(command));
            }
            String stdout = readFile(stdoutPath);
            String stderr = readFile(stderrPath);
            GitCommandResult result = new GitCommandResult(process.exitValue(), stdout, stderr);
            if (result.getExitCode() != 0) {
                throw new BizException(ErrorCode.BIZ_ERROR, "git command failed: " + maskCommand(command) + "; " + stderr);
            }
            return result;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "git command error: " + exception.getMessage());
        } finally {
            deleteQuietly(stdoutPath);
            deleteQuietly(stderrPath);
        }
    }

    private String authUrl(String repoUrl, String token) {
        if (!StringUtils.hasText(token) || !repoUrl.startsWith("https://")) {
            return repoUrl;
        }
        try {
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.name());
            return "https://oauth2:" + encodedToken + "@" + repoUrl.substring("https://".length());
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "encode repository token failed");
        }
    }

    private String safeName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String read(java.io.InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private String readFile(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void deleteQuietly(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
        }
    }

    private String maskCommand(String[] command) {
        List<String> safe = new ArrayList<>(Arrays.asList(command));
        for (int i = 0; i < safe.size(); i++) {
            safe.set(i, MaskUtils.maskSecret(safe.get(i)));
        }
        return String.join(" ", safe);
    }
}
