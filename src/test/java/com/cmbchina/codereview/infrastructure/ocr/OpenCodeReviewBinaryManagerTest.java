package com.cmbchina.codereview.infrastructure.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenCodeReviewBinaryManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsBundledExecutableAndRunsPinnedVersion() throws Exception {
        OpenCodeReviewProperties properties = new OpenCodeReviewProperties();
        properties.setBundledVersion("1.7.9");
        properties.setExtractRoot(tempDir.toString());
        OpenCodeReviewBinaryManager manager = new OpenCodeReviewBinaryManager(properties);

        Path executable = Path.of(manager.resolveCommand());

        assertTrue(Files.isRegularFile(executable));
        Process process = new ProcessBuilder(executable.toString(), "version").start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor());
        assertTrue(output.contains("v1.7.9"));
    }

    @Test
    void keepsExplicitExternalCommandOverride() {
        OpenCodeReviewProperties properties = new OpenCodeReviewProperties();
        properties.setCommand("custom-ocr");

        assertEquals("custom-ocr", new OpenCodeReviewBinaryManager(properties).resolveCommand());
    }
}
