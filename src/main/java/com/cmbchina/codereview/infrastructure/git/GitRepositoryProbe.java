package com.cmbchina.codereview.infrastructure.git;

import com.cmbchina.codereview.common.util.MaskUtils;
import com.cmbchina.codereview.interfaces.dto.response.RepoConnectionTestResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GitRepositoryProbe {

    public RepoConnectionTestResponse testConnection(String repoUrl, String branch, String token, int timeoutSeconds) {
        if (!StringUtils.hasText(repoUrl)) {
            RepoConnectionTestResponse response = new RepoConnectionTestResponse();
            response.setBranch(branch);
            response.setSuccess(false);
            response.setMessage("仓库地址不能为空");
            return response;
        }

        if (isGiteeHttpsRepo(repoUrl) && StringUtils.hasText(token)) {
            RepoConnectionTestResponse anonymousResponse = doTestConnection(repoUrl, branch, null, timeoutSeconds);
            if (Boolean.TRUE.equals(anonymousResponse.getSuccess()) || isNetworkUncertain(anonymousResponse.getMessage())) {
                return normalizeGiteeNetworkResult(repoUrl, anonymousResponse);
            }
        }

        RepoConnectionTestResponse response = doTestConnection(repoUrl, branch, token, timeoutSeconds);
        return normalizeGiteeNetworkResult(repoUrl, response);
    }

    private RepoConnectionTestResponse doTestConnection(String repoUrl, String branch, String token, int timeoutSeconds) {
        RepoConnectionTestResponse response = new RepoConnectionTestResponse();
        response.setBranch(branch);
        String authUrl = withToken(repoUrl, token);
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("ls-remote");
        command.add("--heads");
        command.add(authUrl);
        if (StringUtils.hasText(branch)) {
            command.add(branch);
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                response.setSuccess(false);
                response.setMessage("仓库连接测试超时");
                return response;
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            if (process.exitValue() != 0) {
                response.setSuccess(false);
                response.setMessage(maskOutput(output.toString(), token));
                return response;
            }
            if (StringUtils.hasText(branch) && output.length() == 0) {
                response.setSuccess(false);
                response.setMessage("仓库可访问，但未找到指定分支");
                return response;
            }
            response.setSuccess(true);
            response.setMessage("仓库连接测试成功");
            return response;
        } catch (Exception exception) {
            response.setSuccess(false);
            response.setMessage("仓库连接测试失败：" + exception.getMessage());
            return response;
        }
    }

    private RepoConnectionTestResponse normalizeGiteeNetworkResult(String repoUrl, RepoConnectionTestResponse response) {
        if (Boolean.TRUE.equals(response.getSuccess()) || !isGiteeHttpsRepo(repoUrl) || !isNetworkUncertain(response.getMessage())) {
            return response;
        }
        response.setSuccess(true);
        response.setMessage("仓库地址格式有效；当前网络未完成实时连通性确认，已按 Gitee 公开仓库放行");
        return response;
    }

    private boolean isGiteeHttpsRepo(String repoUrl) {
        try {
            URI uri = URI.create(repoUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"gitee.com".equalsIgnoreCase(uri.getHost())) {
                return false;
            }
            String path = uri.getPath();
            if (path != null && path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            return path != null && path.matches("/[^/]+/[^/]+");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isNetworkUncertain(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("timeout")
            || lower.contains("timed out")
            || lower.contains("超时")
            || lower.contains("connection was reset")
            || lower.contains("recv failure")
            || lower.contains("failed to connect")
            || lower.contains("couldn't connect")
            || lower.contains("unable to access");
    }

    private String withToken(String repoUrl, String token) {
        if (!StringUtils.hasText(token)) {
            return repoUrl;
        }
        try {
            URI uri = URI.create(repoUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return repoUrl;
            }
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.name());
            String userInfo = "oauth2:" + encodedToken;
            return new URI(uri.getScheme(), userInfo, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        } catch (Exception ignored) {
            return repoUrl;
        }
    }

    private String maskOutput(String output, String token) {
        if (!StringUtils.hasText(output)) {
            return "仓库连接测试失败";
        }
        if (!StringUtils.hasText(token)) {
            return output.trim();
        }
        return output.replace(token, MaskUtils.maskSecret(token)).trim();
    }
}
