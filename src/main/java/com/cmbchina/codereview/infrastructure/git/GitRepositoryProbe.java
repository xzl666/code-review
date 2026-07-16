package com.cmbchina.codereview.infrastructure.git;

import com.cmbchina.codereview.interfaces.dto.response.RepoConnectionTestResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GitRepositoryProbe {

    private final GiteeSshCredentialManager credentialManager;

    public GitRepositoryProbe(GiteeSshCredentialManager credentialManager) {
        this.credentialManager = credentialManager;
    }

    public RepoConnectionTestResponse testConnection(String repoUrl, String branch, int timeoutSeconds) {
        if (!StringUtils.hasText(repoUrl)) {
            RepoConnectionTestResponse response = new RepoConnectionTestResponse();
            response.setBranch(branch);
            response.setSuccess(false);
            response.setMessage("仓库地址不能为空");
            return response;
        }

        return doTestConnection(repoUrl, branch, timeoutSeconds);
    }

    private RepoConnectionTestResponse doTestConnection(String repoUrl, String branch, int timeoutSeconds) {
        RepoConnectionTestResponse response = new RepoConnectionTestResponse();
        response.setBranch(branch);
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("git"); command.add("ls-remote"); command.add("--heads");
        command.add(credentialManager.normalizeRepositoryUrl(repoUrl));
        if (StringUtils.hasText(branch)) {
            command.add(branch);
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(credentialManager.gitEnvironment());
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
                response.setMessage(StringUtils.hasText(output) ? output.toString().trim() : "仓库连接测试失败");
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

}
