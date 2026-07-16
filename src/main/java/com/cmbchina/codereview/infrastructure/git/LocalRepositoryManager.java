package com.cmbchina.codereview.infrastructure.git;

import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.common.util.MaskUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
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

    private final GiteeSshCredentialManager credentialManager;

    public LocalRepositoryManager(GiteeSshCredentialManager credentialManager) {
        this.credentialManager = credentialManager;
    }

    public Path prepare(Project project, String branch) {
        try {
            Files.createDirectories(repoRoot);
            Path repoDir = repoRoot.resolve("project-" + project.getId());
            if (Files.exists(repoDir.resolve(".git"))) {
                refresh(repoDir, project.getRepoUrl(), branch);
                return repoDir;
            }
            String repoUrl = credentialManager.normalizeRepositoryUrl(project.getRepoUrl());
            runRemote(repoRoot, "git", "clone", "--branch", branch, "--single-branch", repoUrl, repoDir.toAbsolutePath().toString());
            return repoDir;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "prepare repository failed: " + exception.getMessage());
        }
    }

    public String ensureRef(Path repoDir, String ref) {
        if (!StringUtils.hasText(ref)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Git ref 不能为空");
        }
        if (canResolve(repoDir, ref)) {
            return ref;
        }
        String remoteRef = "origin/" + ref;
        if (canResolve(repoDir, remoteRef)) {
            return remoteRef;
        }
        runRemote(repoDir, "git", "fetch", "origin", ref, "--depth=200");
        if (canResolve(repoDir, ref)) {
            return ref;
        }
        if (canResolve(repoDir, remoteRef)) {
            return remoteRef;
        }
        if (canResolve(repoDir, "FETCH_HEAD")) {
            return run(repoDir, "git", "rev-parse", "FETCH_HEAD^{commit}").getStdout().trim();
        }
        throw new BizException(ErrorCode.BIZ_ERROR, "无法解析 Git ref: " + ref);
    }

    private boolean canResolve(Path repoDir, String ref) {
        try {
            run(repoDir, "git", "rev-parse", "--verify", ref + "^{commit}");
            return true;
        } catch (BizException ignored) {
            return false;
        }
    }

    private void refresh(Path repoDir, String repoUrl, String branch) {
        try {
            String sshUrl = credentialManager.normalizeRepositoryUrl(repoUrl);
            run(repoDir, "git", "remote", "set-url", "origin", sshUrl);
            runRemote(repoDir, "git", "fetch", "origin", branchRefSpec(branch), "--prune", "--depth=200");
            run(repoDir, "git", "checkout", "-B", branch, "origin/" + branch);
        } catch (BizException exception) {
            if (isNetworkIssue(exception.getMessage())) {
                throw new BizException(ErrorCode.BIZ_ERROR,
                    "当前机器无法连接远程仓库，分支 " + branch + " 未能拉取。请检查 Gitee 网络连通性、代理或防火墙后重试。原始错误：" + exception.getMessage());
            }
            throw new BizException(ErrorCode.BIZ_ERROR, "refresh repository branch " + branch + " failed: " + exception.getMessage());
        }
    }

    private String branchRefSpec(String branch) {
        return "+refs/heads/" + branch + ":refs/remotes/origin/" + branch;
    }

    public GitCommandResult run(Path workingDirectory, String... command) {
        return runInternal(workingDirectory, false, command);
    }

    private GitCommandResult runRemote(Path workingDirectory, String... command) {
        return runInternal(workingDirectory, true, command);
    }

    private GitCommandResult runInternal(Path workingDirectory, boolean remote, String... command) {
        Path stdoutPath = null;
        Path stderrPath = null;
        try {
            stdoutPath = Files.createTempFile("code-review-git-stdout-", ".log");
            stderrPath = Files.createTempFile("code-review-git-stderr-", ".log");
            ProcessBuilder builder = new ProcessBuilder(command);
            if (remote) {
                builder.environment().putAll(credentialManager.gitEnvironment());
            }
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

    private boolean isNetworkIssue(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("connection was reset")
            || lower.contains("recv failure")
            || lower.contains("failed to connect")
            || lower.contains("couldn't connect")
            || lower.contains("timed out")
            || lower.contains("timeout")
            || lower.contains("unable to access");
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
