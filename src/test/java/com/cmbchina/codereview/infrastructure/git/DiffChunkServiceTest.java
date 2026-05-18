package com.cmbchina.codereview.infrastructure.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiffChunkServiceTest {

    private final DiffChunkService diffChunkService = new DiffChunkService();

    @Test
    void splitKeepsHunkBoundariesAndLineMetadata() {
        GitDiffSummary summary = new GitDiffSummary();
        summary.setDiffContent(String.join("\n",
            "diff --git a/src/App.java b/src/App.java",
            "index 111..222 100644",
            "--- a/src/App.java",
            "+++ b/src/App.java",
            "@@ -10,2 +10,3 @@",
            " line10",
            "+line11",
            "@@ -80,2 +81,3 @@",
            " line80",
            "+line81",
            ""
        ));

        List<DiffChunk> chunks = diffChunkService.split(summary, 160);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getFilePath()).isEqualTo("src/App.java");
        assertThat(chunks.get(0).getOldStartLine()).isEqualTo(10);
        assertThat(chunks.get(0).getNewStartLine()).isEqualTo(10);
        assertThat(chunks.get(0).getContent()).contains("@@ -10,2 +10,3 @@");
        assertThat(chunks.get(0).getContent()).doesNotContain("@@ -80,2 +81,3 @@");
        assertThat(chunks.get(1).getOldStartLine()).isEqualTo(80);
        assertThat(chunks.get(1).getNewStartLine()).isEqualTo(81);
    }
}
