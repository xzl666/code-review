package com.cmbchina.codereview.infrastructure.git;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DiffChunkService {

    public List<DiffChunk> split(GitDiffSummary diffSummary, int maxChars) {
        List<DiffChunk> chunks = new ArrayList<>();
        String diffContent = diffSummary.getDiffContent();
        if (!StringUtils.hasText(diffContent)) {
            return chunks;
        }
        String[] fileDiffs = diffContent.split("(?m)(?=^diff --git )");
        int chunkIndex = 1;
        for (String fileDiff : fileDiffs) {
            if (!StringUtils.hasText(fileDiff)) {
                continue;
            }
            String filePath = parseFilePath(fileDiff);
            if (fileDiff.length() <= maxChars) {
                chunks.add(new DiffChunk(filePath, chunkIndex++, fileDiff));
                continue;
            }
            int offset = 0;
            while (offset < fileDiff.length()) {
                int end = Math.min(offset + maxChars, fileDiff.length());
                chunks.add(new DiffChunk(filePath, chunkIndex++, fileDiff.substring(offset, end)));
                offset = end;
            }
        }
        return chunks;
    }

    private String parseFilePath(String fileDiff) {
        String firstLine = fileDiff.split("\\R", 2)[0];
        String[] parts = firstLine.split("\\s+");
        if (parts.length >= 4) {
            return parts[3].replaceFirst("^b/", "");
        }
        return "UNKNOWN";
    }
}
