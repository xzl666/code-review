package com.cmbchina.codereview.infrastructure.ocr;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenCodeReviewBinaryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenCodeReviewBinaryManager.class);

    private final OpenCodeReviewProperties properties;
    private volatile String resolvedCommand;

    public OpenCodeReviewBinaryManager(OpenCodeReviewProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        String command = resolveCommand();
        LOGGER.info("OpenCodeReview executable ready: {}", command);
    }

    public synchronized String resolveCommand() {
        if (StringUtils.hasText(resolvedCommand)) {
            return resolvedCommand;
        }
        if (StringUtils.hasText(properties.getCommand())) {
            resolvedCommand = properties.getCommand().trim();
            return resolvedCommand;
        }
        try {
            String platform = platform();
            String fileName = isWindows() ? "opencodereview.exe" : "opencodereview";
            String resourcePath = "ocr/" + platform + "/" + fileName;
            ClassPathResource binaryResource = new ClassPathResource(resourcePath + ".gz");
            ClassPathResource hashResource = new ClassPathResource(resourcePath + ".sha256");
            if (!binaryResource.exists() || !hashResource.exists()) {
                throw new IllegalStateException("当前构建未包含 " + platform
                    + " 的 OCR 二进制，请先执行 scripts/build-ocr 构建对应平台资源");
            }
            String expectedHash;
            try (InputStream input = hashResource.getInputStream()) {
                expectedHash = new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim().toLowerCase();
            }
            Path directory = extractRoot().resolve(properties.getBundledVersion()).resolve(platform);
            Files.createDirectories(directory);
            Path executable = directory.resolve(fileName);
            if (!Files.isRegularFile(executable) || !expectedHash.equals(sha256(executable))) {
                Path temporary = Files.createTempFile(directory, fileName, ".tmp");
                try (InputStream input = new GZIPInputStream(binaryResource.getInputStream())) {
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                if (!expectedHash.equals(sha256(temporary))) {
                    Files.deleteIfExists(temporary);
                    throw new IllegalStateException("内置 OCR 二进制 SHA-256 校验失败");
                }
                move(temporary, executable);
            }
            executable.toFile().setExecutable(true, false);
            resolvedCommand = executable.toAbsolutePath().toString();
            return resolvedCommand;
        } catch (Exception exception) {
            throw new IllegalStateException("释放内置 OpenCodeReview 失败：" + exception.getMessage(), exception);
        }
    }

    private Path extractRoot() {
        if (StringUtils.hasText(properties.getExtractRoot())) {
            return Paths.get(properties.getExtractRoot());
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "code-review", "ocr");
    }

    private String platform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String architecture = System.getProperty("os.arch", "").toLowerCase();
        String osName = os.contains("win") ? "windows" : os.contains("mac") ? "darwin" : os.contains("linux") ? "linux" : null;
        String archName = architecture.contains("aarch64") || architecture.contains("arm64") ? "arm64"
            : architecture.contains("amd64") || architecture.contains("x86_64") ? "amd64" : null;
        if (osName == null || archName == null) {
            throw new IllegalStateException("不支持的平台：" + os + "/" + architecture);
        }
        return osName + "-" + archName;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, length);
            }
        }
        StringBuilder value = new StringBuilder();
        for (byte item : digest.digest()) {
            value.append(String.format("%02x", item));
        }
        return value.toString();
    }

    private void move(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
