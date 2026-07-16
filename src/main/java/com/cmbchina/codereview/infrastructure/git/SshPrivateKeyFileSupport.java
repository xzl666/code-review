package com.cmbchina.codereview.infrastructure.git;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class SshPrivateKeyFileSupport {

    private SshPrivateKeyFileSupport() {
    }

    static String normalizeContent(String privateKey) {
        return privateKey.replace("\\n", "\n")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim() + "\n";
    }

    static void restrictToCurrentUser(Path path) throws Exception {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            Process process = new ProcessBuilder(
                "icacls",
                path.toAbsolutePath().toString(),
                "/inheritance:r",
                "/grant:r",
                System.getProperty("user.name") + ":(F)"
            ).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                throw new IllegalStateException("无法限制 SSH 私钥文件权限");
            }
            return;
        }
        Set<PosixFilePermission> permissions = Collections.singleton(PosixFilePermission.OWNER_READ);
        Files.setPosixFilePermissions(path, permissions);
    }
}
