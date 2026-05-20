package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiIssueLocationNormalizer {

    public void normalize(ReviewIssueEntity issue, DiffChunk chunk) {
        List<ChangedLine> changedLines = changedLines(chunk);
        if (changedLines.isEmpty()) {
            clearLocation(issue);
            return;
        }
        if (overlapsChangedLine(issue.getStartLine(), issue.getEndLine(), changedLines)) {
            issue.setCodeSnippet(snippet(changedLines, issue.getStartLine(), issue.getEndLine()));
            return;
        }
        ChangedLine relocated = relocate(issue, changedLines);
        if (relocated == null) {
            clearLocation(issue);
            return;
        }
        issue.setStartLine(relocated.lineNumber);
        issue.setEndLine(relocated.lineNumber);
        issue.setCodeSnippet(snippet(changedLines, relocated.lineNumber, relocated.lineNumber));
    }

    private List<ChangedLine> changedLines(DiffChunk chunk) {
        List<ChangedLine> changedLines = new ArrayList<>();
        if (chunk == null || !StringUtils.hasText(chunk.getContent())) {
            return changedLines;
        }
        int currentNewLine = chunk.getNewStartLine() == null ? 0 : chunk.getNewStartLine();
        for (String line : chunk.getContent().split("\\R", -1)) {
            if (line.startsWith("@@")) {
                currentNewLine = parseNewStartLine(line, currentNewLine);
                continue;
            }
            if (line.startsWith("---") || line.startsWith("+++")) {
                continue;
            }
            if (line.startsWith("-")) {
                continue;
            }
            if (line.startsWith("+")) {
                changedLines.add(new ChangedLine(currentNewLine, line));
            }
            if (!line.startsWith("\\ No newline at end of file")) {
                currentNewLine++;
            }
        }
        return changedLines;
    }

    private boolean overlapsChangedLine(Integer startLine, Integer endLine, List<ChangedLine> changedLines) {
        if (startLine == null || startLine < 1) {
            return false;
        }
        int start = startLine;
        int end = endLine == null || endLine < startLine ? startLine : endLine;
        for (ChangedLine changedLine : changedLines) {
            if (changedLine.lineNumber >= start && changedLine.lineNumber <= end) {
                return true;
            }
        }
        return false;
    }

    private ChangedLine relocate(ReviewIssueEntity issue, List<ChangedLine> changedLines) {
        String issueText = ((issue.getSummary() == null ? "" : issue.getSummary()) + " "
            + (issue.getDetail() == null ? "" : issue.getDetail()) + " "
            + (issue.getSuggestion() == null ? "" : issue.getSuggestion()) + " "
            + (issue.getCodeSnippet() == null ? "" : issue.getCodeSnippet())).toLowerCase();
        ChangedLine bestLine = null;
        int bestScore = 0;
        for (ChangedLine changedLine : changedLines) {
            int score = score(issueText, changedLine.content);
            if (score > bestScore) {
                bestScore = score;
                bestLine = changedLine;
            }
        }
        return bestScore >= 2 ? bestLine : null;
    }

    private int score(String issueText, String line) {
        int score = 0;
        for (String token : tokens(line)) {
            if (issueText.contains(token.toLowerCase())) {
                score++;
            }
        }
        return score;
    }

    private Set<String> tokens(String line) {
        Set<String> tokens = new HashSet<>();
        for (String token : line.split("[^A-Za-z0-9_]+")) {
            if (token.length() >= 4 && !isCommonToken(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean isCommonToken(String token) {
        String lower = token.toLowerCase();
        return "public".equals(lower)
            || "private".equals(lower)
            || "protected".equals(lower)
            || "static".equals(lower)
            || "final".equals(lower)
            || "return".equals(lower)
            || "null".equals(lower)
            || "true".equals(lower)
            || "false".equals(lower);
    }

    private String snippet(List<ChangedLine> changedLines, Integer startLine, Integer endLine) {
        if (startLine == null || startLine < 1) {
            return "";
        }
        int start = startLine;
        int end = endLine == null || endLine < startLine ? startLine : endLine;
        StringBuilder builder = new StringBuilder();
        for (ChangedLine changedLine : changedLines) {
            if (changedLine.lineNumber < start || changedLine.lineNumber > end) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append(changedLine.lineNumber)
                .append("  ")
                .append(changedLine.content);
        }
        return builder.toString();
    }

    private void clearLocation(ReviewIssueEntity issue) {
        issue.setStartLine(null);
        issue.setEndLine(null);
        issue.setCodeSnippet("");
    }

    private int parseNewStartLine(String hunkHeader, int fallback) {
        int plusIndex = hunkHeader.indexOf('+');
        if (plusIndex < 0) {
            return fallback;
        }
        int index = plusIndex + 1;
        StringBuilder number = new StringBuilder();
        while (index < hunkHeader.length() && Character.isDigit(hunkHeader.charAt(index))) {
            number.append(hunkHeader.charAt(index));
            index++;
        }
        return number.length() == 0 ? fallback : Integer.parseInt(number.toString());
    }

    private static class ChangedLine {
        private final int lineNumber;
        private final String content;

        private ChangedLine(int lineNumber, String content) {
            this.lineNumber = lineNumber;
            this.content = content;
        }
    }
}
