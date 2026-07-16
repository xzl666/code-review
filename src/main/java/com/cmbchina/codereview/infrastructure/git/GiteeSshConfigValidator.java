package com.cmbchina.codereview.infrastructure.git;

import com.cmbchina.codereview.interfaces.dto.response.ConfigValidationResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GiteeSshConfigValidator {

    private static final int MAX_OUTPUT_LENGTH = 2000;
    private final Path keyDirectory = Paths.get(System.getProperty("user.home"), ".code-review", "ssh");

    public ConfigValidationResponse validate(String baseUrl, String privateKey, int timeoutSeconds) {
        if (!StringUtils.hasText(baseUrl)) {
            return failed("Gitee 地址不能为空");
        }
        if (!StringUtils.hasText(privateKey)) {
            return failed("SSH 私钥未配置");
        }

        Path keyFile = null;
        Path outputFile = null;
        try {
            SshEndpoint endpoint = endpoint(baseUrl);
            Files.createDirectories(keyDirectory);
            keyFile = Files.createTempFile(keyDirectory, "gitee_validate_", ".key");
            outputFile = Files.createTempFile(keyDirectory, "gitee_validate_", ".log");
            Files.write(keyFile, (privateKey.replace("\\n", "\n").trim() + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8));
            SshPrivateKeyFileSupport.restrictToCurrentUser(keyFile);

            ConfigValidationResponse keyResult = validateKeyFile(keyFile);
            if (!Boolean.TRUE.equals(keyResult.getSuccess())) {
                return keyResult;
            }

            List<String> command = new ArrayList<>();
            command.add("ssh");
            command.add("-i");
            command.add(keyFile.toAbsolutePath().toString());
            command.add("-o");
            command.add("IdentitiesOnly=yes");
            command.add("-o");
            command.add("StrictHostKeyChecking=accept-new");
            command.add("-o");
            command.add("BatchMode=yes");
            command.add("-o");
            command.add("ConnectTimeout=" + Math.max(1, timeoutSeconds));
            if (endpoint.port != 22) {
                command.add("-p");
                command.add(String.valueOf(endpoint.port));
            }
            command.add("-T");
            command.add("git@" + endpoint.host);

            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
            if (!process.waitFor(Math.max(1, timeoutSeconds) + 2L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return failed("Gitee SSH 连接验证超时");
            }
            String output = sanitizeOutput(new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8));
            ConfigValidationResponse response = new ConfigValidationResponse();
            response.setStatusCode(process.exitValue());
            response.setResponseBody(output);
            response.setSuccess(process.exitValue() == 0);
            response.setMessage(process.exitValue() == 0
                ? "Gitee SSH 配置验证通过"
                : "Gitee SSH 配置验证失败" + (StringUtils.hasText(output) ? "：" + output : ""));
            return response;
        } catch (IllegalArgumentException exception) {
            return failed(exception.getMessage());
        } catch (Exception exception) {
            return failed("Gitee SSH 配置验证失败：" + exception.getMessage());
        } finally {
            deleteQuietly(keyFile);
            deleteQuietly(outputFile);
        }
    }

    private ConfigValidationResponse validateKeyFile(Path keyFile) throws Exception {
        Process process = new ProcessBuilder("ssh-keygen", "-y", "-f", keyFile.toAbsolutePath().toString())
            .redirectErrorStream(true)
            .start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        String output = sanitizeOutput(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
            .replace(keyFile.toAbsolutePath().toString(), "<临时私钥>")
            .replace(keyFile.toAbsolutePath().toString().replace('\\', '/'), "<临时私钥>");
        if (!finished) {
            process.destroyForcibly();
            return failed("SSH 私钥格式校验超时");
        }
        if (process.exitValue() != 0) {
            return failed("SSH 私钥无效、已加密或格式不受支持" + (StringUtils.hasText(output) ? "：" + truncate(output) : ""));
        }
        ConfigValidationResponse response = new ConfigValidationResponse();
        response.setSuccess(true);
        response.setMessage("SSH 私钥格式有效");
        return response;
    }

    private SshEndpoint endpoint(String baseUrl) {
        String value = baseUrl.trim();
        URI uri = URI.create(value.contains("://") ? value : "https://" + value);
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("Gitee 地址格式不正确");
        }
        int port = "ssh".equalsIgnoreCase(uri.getScheme()) && uri.getPort() > 0 ? uri.getPort() : 22;
        return new SshEndpoint(uri.getHost(), port);
    }

    private ConfigValidationResponse failed(String message) {
        ConfigValidationResponse response = new ConfigValidationResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    private String truncate(String value) {
        return value != null && value.length() > MAX_OUTPUT_LENGTH
            ? value.substring(0, MAX_OUTPUT_LENGTH) + "..."
            : value;
    }

    private String sanitizeOutput(String value) {
        String clean = value == null ? "" : value.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "").trim();
        return truncate(clean);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private static final class SshEndpoint {
        private final String host;
        private final int port;

        private SshEndpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
