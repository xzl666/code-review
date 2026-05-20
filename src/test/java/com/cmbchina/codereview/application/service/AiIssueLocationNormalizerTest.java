package com.cmbchina.codereview.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import org.junit.jupiter.api.Test;

class AiIssueLocationNormalizerTest {

    private final AiIssueLocationNormalizer normalizer = new AiIssueLocationNormalizer();

    @Test
    void clearsLocationWhenAiLinePointsToUnchangedContext() {
        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setStartLine(233);
        issue.setEndLine(233);
        issue.setSummary("getDateRangeList HOUR branch is missing break");
        issue.setDetail("case HOUR falls through to case DAY");

        normalizer.normalize(issue, chunk());

        assertNull(issue.getStartLine());
        assertNull(issue.getEndLine());
        assertEquals("", issue.getCodeSnippet());
    }

    @Test
    void relocatesIssueToChangedLineWhenTextMatchesAddedCode() {
        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setSummary("getDateList uses manual ArrayList loop");
        issue.setDetail("dateList add startDate plusDays in loop");

        normalizer.normalize(issue, chunk());

        assertEquals(239, issue.getStartLine());
        assertEquals(239, issue.getEndLine());
        assertTrue(issue.getCodeSnippet().contains("getDateList"));
    }

    private DiffChunk chunk() {
        return new DiffChunk(
            "src/LocalDateTimeUtils.java",
            1,
            225,
            225,
            String.join("\n",
                "diff --git a/src/LocalDateTimeUtils.java b/src/LocalDateTimeUtils.java",
                "--- a/src/LocalDateTimeUtils.java",
                "+++ b/src/LocalDateTimeUtils.java",
                "@@ -225,20 +225,26 @@",
                "     public static LocalDateTime getYear() {",
                "         return LocalDateTime.now();",
                "     }",
                "     public static List<LocalDateTime[]> getDateRangeList(LocalDateTime startTime,",
                "                                                          LocalDateTime endTime,",
                "                                                          Integer interval) {",
                "         switch (intervalEnum) {",
                "             case HOUR:",
                "                 while (startTime.isBefore(endTime)) {",
                "                     startTime = startTime.plusHours(1);",
                "                 }",
                "             case DAY:",
                "                 break;",
                "         }",
                "+    public static List<LocalDate> getDateList(LocalDate startDate, int days) {",
                "+        List<LocalDate> dateList = new ArrayList<>(days);",
                "+        for (int i = 0; i < days; i++) {",
                "+            dateList.add(startDate.plusDays(i));",
                "+        }",
                "+        return dateList;",
                "+    }",
                ""
            )
        );
    }
}
