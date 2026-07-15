package com.cmbchina.codereview.infrastructure.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitDiffServiceWarningTest {

    @Test
    @SuppressWarnings("unchecked")
    void warningsIncludeSkippedCommitAndFileCounts() throws Exception {
        GitDiffService service = new GitDiffService(null, new GitDiffProperties());
        GitDiffSummary summary = new GitDiffSummary();
        summary.setSkippedCommitCount(2);
        summary.setSkippedFileCount(7);

        Method method = GitDiffService.class.getDeclaredMethod("warnings", GitDiffSummary.class);
        method.setAccessible(true);
        List<String> warnings = (List<String>) method.invoke(service, summary);

        assertThat(warnings)
            .contains("Skipped 2 oversized or timed-out commits.")
            .contains("Skipped 7 files because their git diff commands failed or timed out.");
    }
}
