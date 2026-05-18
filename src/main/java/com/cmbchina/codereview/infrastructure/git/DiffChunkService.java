package com.cmbchina.codereview.infrastructure.git;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DiffChunkService {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");

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
            for (HunkGroup group : splitFileByHunks(fileDiff, maxChars)) {
                chunks.add(new DiffChunk(filePath, chunkIndex++, group.oldStartLine, group.newStartLine, group.content));
            }
        }
        return chunks;
    }

    private List<HunkGroup> splitFileByHunks(String fileDiff, int maxChars) {
        String header = fileHeader(fileDiff);
        List<String> hunks = hunks(fileDiff);
        List<HunkGroup> groups = new ArrayList<>();
        if (hunks.isEmpty()) {
            addWithHardLimit(groups, header, null, null, maxChars);
            return groups;
        }
        StringBuilder builder = new StringBuilder(header);
        Integer groupOldStart = null;
        Integer groupNewStart = null;
        for (String hunk : hunks) {
            HunkPosition position = parseHunkPosition(hunk);
            if (builder.length() > header.length() && builder.length() + hunk.length() > maxChars) {
                groups.add(new HunkGroup(groupOldStart, groupNewStart, builder.toString()));
                builder = new StringBuilder(header);
                groupOldStart = null;
                groupNewStart = null;
            }
            if (hunk.length() + header.length() > maxChars) {
                if (builder.length() > header.length()) {
                    groups.add(new HunkGroup(groupOldStart, groupNewStart, builder.toString()));
                    builder = new StringBuilder(header);
                    groupOldStart = null;
                    groupNewStart = null;
                }
                addWithHardLimit(groups, header + hunk, position.oldStartLine, position.newStartLine, maxChars);
                continue;
            }
            if (groupOldStart == null) {
                groupOldStart = position.oldStartLine;
                groupNewStart = position.newStartLine;
            }
            builder.append(hunk);
        }
        if (builder.length() > header.length()) {
            groups.add(new HunkGroup(groupOldStart, groupNewStart, builder.toString()));
        }
        return groups;
    }

    private void addWithHardLimit(List<HunkGroup> groups, String content, Integer oldStartLine, Integer newStartLine, int maxChars) {
        int offset = 0;
        while (offset < content.length()) {
            int end = Math.min(offset + maxChars, content.length());
            groups.add(new HunkGroup(oldStartLine, newStartLine, content.substring(offset, end)));
            offset = end;
        }
    }

    private String fileHeader(String fileDiff) {
        int hunkStart = indexOfHunk(fileDiff);
        if (hunkStart < 0) {
            return fileDiff;
        }
        return fileDiff.substring(0, hunkStart);
    }

    private List<String> hunks(String fileDiff) {
        List<String> result = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?m)^@@ ").matcher(fileDiff);
        while (matcher.find()) {
            starts.add(matcher.start());
        }
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : fileDiff.length();
            result.add(fileDiff.substring(start, end));
        }
        return result;
    }

    private int indexOfHunk(String fileDiff) {
        Matcher matcher = Pattern.compile("(?m)^@@ ").matcher(fileDiff);
        return matcher.find() ? matcher.start() : -1;
    }

    private HunkPosition parseHunkPosition(String hunk) {
        String firstLine = hunk.split("\\R", 2)[0];
        Matcher matcher = HUNK_HEADER.matcher(firstLine);
        if (!matcher.matches()) {
            return new HunkPosition(null, null);
        }
        return new HunkPosition(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    private String parseFilePath(String fileDiff) {
        String firstLine = fileDiff.split("\\R", 2)[0];
        String[] parts = firstLine.split("\\s+");
        if (parts.length >= 4) {
            return parts[3].replaceFirst("^b/", "");
        }
        return "UNKNOWN";
    }

    private static class HunkGroup {
        private final Integer oldStartLine;
        private final Integer newStartLine;
        private final String content;

        private HunkGroup(Integer oldStartLine, Integer newStartLine, String content) {
            this.oldStartLine = oldStartLine;
            this.newStartLine = newStartLine;
            this.content = content;
        }
    }

    private static class HunkPosition {
        private final Integer oldStartLine;
        private final Integer newStartLine;

        private HunkPosition(Integer oldStartLine, Integer newStartLine) {
            this.oldStartLine = oldStartLine;
            this.newStartLine = newStartLine;
        }
    }
}
