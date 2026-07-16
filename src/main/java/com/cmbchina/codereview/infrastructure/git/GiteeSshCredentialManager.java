package com.cmbchina.codereview.infrastructure.git;

import com.cmbchina.codereview.application.service.SystemConfigAppService;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GiteeSshCredentialManager {

    private final SystemConfigAppService systemConfigAppService;
    private final Path keyPath = Paths.get(System.getProperty("user.home"), ".code-review", "ssh", "gitee_key");

    public GiteeSshCredentialManager(SystemConfigAppService systemConfigAppService) {
        this.systemConfigAppService = systemConfigAppService;
    }

    public String normalizeRepositoryUrl(String repositoryUrl) {
        if (!StringUtils.hasText(repositoryUrl)) {
            return repositoryUrl;
        }
        String value = repositoryUrl.trim();
        if (value.startsWith("git@") || value.startsWith("ssh://")) {
            return value;
        }
        if (!value.contains("://")) {
            return "git@" + configuredHost() + ":" + normalizePath(value);
        }
        try {
            URI uri = URI.create(value);
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                return "git@" + uri.getHost() + ":" + normalizePath(uri.getPath());
            }
        } catch (Exception ignored) {
        }
        return value;
    }

    public Map<String, String> gitEnvironment() {
        String privateKey = systemConfigAppService.getGiteeSshPrivateKey();
        if (!StringUtils.hasText(privateKey)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Gitee SSH 私钥未配置，请先在系统配置中维护私钥");
        }
        materialize(privateKey);
        Map<String, String> environment = new LinkedHashMap<>();
        String path = keyPath.toAbsolutePath().toString().replace('\\', '/');
        environment.put("GIT_SSH_COMMAND", "ssh -i \"" + path
            + "\" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -o BatchMode=yes");
        environment.put("GIT_TERMINAL_PROMPT", "0");
        return environment;
    }

    private void materialize(String privateKey) {
        try {
            // Git for Windows uses its bundled OpenSSH, which rejects OpenSSH keys ending in CRLF.
            String normalized = SshPrivateKeyFileSupport.normalizeContent(privateKey);
            Files.createDirectories(keyPath.getParent());
            if (!Files.exists(keyPath) || !normalized.equals(new String(Files.readAllBytes(keyPath), StandardCharsets.UTF_8))) {
                Files.write(keyPath, normalized.getBytes(StandardCharsets.UTF_8));
            }
            SshPrivateKeyFileSupport.restrictToCurrentUser(keyPath);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "写入 Gitee SSH 私钥失败：" + exception.getMessage());
        }
    }

    private String configuredHost() {
        try {
            URI uri = URI.create(systemConfigAppService.getGiteeBaseUrl());
            return StringUtils.hasText(uri.getHost()) ? uri.getHost() : systemConfigAppService.getGiteeBaseUrl();
        } catch (Exception ignored) {
            return systemConfigAppService.getGiteeBaseUrl();
        }
    }

    private String normalizePath(String path) {
        String value = path == null ? "" : path.replaceFirst("^/+", "");
        return value.endsWith(".git") ? value : value + ".git";
    }
}
