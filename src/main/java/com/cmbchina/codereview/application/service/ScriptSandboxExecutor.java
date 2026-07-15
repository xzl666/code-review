package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ScriptSandboxExecutor {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private static final int MAX_TIMEOUT_SECONDS = 60;

    private static final int DEFAULT_MAX_OUTPUT_CHARS = 200000;

    private static final int MAX_INPUT_CHARS = 500000;

    private static final List<String> FORBIDDEN_TOKENS = Arrays.asList(
        "rm -rf",
        "del /",
        "rmdir /s",
        "format ",
        "shutdown",
        "reboot",
        "mkfs",
        "diskpart",
        "reg delete",
        "powershell",
        "pwsh",
        "curl ",
        "wget ",
        "scp ",
        "ssh ",
        "ftp ",
        "nc ",
        "netcat",
        "subprocess.",
        "os.system",
        "child_process"
    );

    public ScriptExecutionResult execute(ScriptExecutionRequest request) {
        Path workDir = null;
        Path stdoutPath = null;
        Path stderrPath = null;
        Process process = null;
        try {
            validate(request);
            workDir = Files.createTempDirectory("code-review-script-sandbox-");
            Path scriptPath = writeScript(workDir, request.getLanguage(), request.getContent());
            stdoutPath = workDir.resolve("stdout.log");
            stderrPath = workDir.resolve("stderr.log");

            ProcessBuilder builder = new ProcessBuilder(command(request.getLanguage(), scriptPath));
            builder.directory(workDir.toFile());
            builder.redirectOutput(stdoutPath.toFile());
            builder.redirectError(stderrPath.toFile());
            applyEnvironmentWhitelist(builder.environment(), workDir);

            process = builder.start();
            if (StringUtils.hasText(request.getInputJson())) {
                process.getOutputStream().write(request.getInputJson().getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();

            int timeoutSeconds = normalizedTimeout(request.getTimeoutSeconds());
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                killProcessTree(process);
                return result(false, null, "", "脚本执行超时", true, false);
            }
            String stdout = limit(readFile(stdoutPath), maxOutputChars(request.getMaxOutputChars()));
            String stderr = limit(readFile(stderrPath), maxOutputChars(request.getMaxOutputChars()));
            return result(process.exitValue() == 0, process.exitValue(), stdout, stderr, false, false);
        } catch (BizException exception) {
            return result(false, null, "", exception.getMessage(), false, true);
        } catch (Exception exception) {
            return result(false, null, "", exception.getMessage(), false, false);
        } finally {
            if (process != null && process.isAlive()) {
                killProcessTree(process);
            }
            deleteQuietly(workDir);
        }
    }

    private void validate(ScriptExecutionRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "脚本执行请求不能为空");
        }
        if (!StringUtils.hasText(request.getLanguage())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "脚本语言不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "脚本内容不能为空");
        }
        if (request.getInputJson() != null && request.getInputJson().length() > MAX_INPUT_CHARS) {
            throw new BizException(ErrorCode.PARAM_ERROR, "脚本输入超过安全上限");
        }
        rejectDangerousContent(request.getContent());
    }

    private void rejectDangerousContent(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        for (String token : FORBIDDEN_TOKENS) {
            if (normalized.contains(token)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "脚本包含受限命令或高风险调用：" + token.trim());
            }
        }
    }

    private Path writeScript(Path workDir, String language, String content) throws Exception {
        String normalizedLanguage = language.toUpperCase(Locale.ROOT);
        if (!"PYTHON".equals(normalizedLanguage)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "脚本语言仅支持 PYTHON");
        }
        String suffix = ".py";
        Path scriptPath = workDir.resolve("script" + suffix);
        Files.write(scriptPath, content.getBytes(StandardCharsets.UTF_8));
        return scriptPath;
    }

    private List<String> command(String language, Path scriptPath) {
        String normalizedLanguage = language.toUpperCase(Locale.ROOT);
        if ("PYTHON".equals(normalizedLanguage)) {
            return Arrays.asList("python", scriptPath.toAbsolutePath().toString());
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "脚本语言仅支持 PYTHON");
    }

    private void applyEnvironmentWhitelist(Map<String, String> environment, Path workDir) {
        Map<String, String> original = new HashMap<>(environment);
        environment.clear();
        copyIfPresent(original, environment, "PATH");
        copyIfPresent(original, environment, "Path");
        copyIfPresent(original, environment, "SystemRoot");
        copyIfPresent(original, environment, "WINDIR");
        copyIfPresent(original, environment, "COMSPEC");
        environment.put("TMP", workDir.toAbsolutePath().toString());
        environment.put("TEMP", workDir.toAbsolutePath().toString());
        environment.put("HOME", workDir.toAbsolutePath().toString());
        environment.put("CODE_REVIEW_SCRIPT_SANDBOX", "true");
    }

    private void copyIfPresent(Map<String, String> original, Map<String, String> target, String key) {
        String value = original.get(key);
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private int normalizedTimeout(Integer timeoutSeconds) {
        int value = timeoutSeconds == null ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        return Math.max(1, Math.min(value, MAX_TIMEOUT_SECONDS));
    }

    private int maxOutputChars(Integer maxOutputChars) {
        return maxOutputChars == null || maxOutputChars <= 0 ? DEFAULT_MAX_OUTPUT_CHARS : maxOutputChars;
    }

    private String readFile(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    private ScriptExecutionResult result(Boolean success,
                                         Integer exitCode,
                                         String stdout,
                                         String stderr,
                                         Boolean timeout,
                                         Boolean securityBlocked) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(success);
        result.setExitCode(exitCode);
        result.setStdout(stdout);
        result.setStderr(stderr);
        result.setTimeout(timeout);
        result.setSecurityBlocked(securityBlocked);
        return result;
    }

    private void killProcessTree(Process process) {
        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(ProcessHandle::destroyForcibly);
        handle.destroyForcibly();
    }

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                .sorted((left, right) -> right.compareTo(left))
                .forEach(item -> {
                    try {
                        Files.deleteIfExists(item);
                    } catch (Exception ignored) {
                    }
                });
        } catch (Exception ignored) {
        }
    }
}
