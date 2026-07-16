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
            .contains("为控制检视范围，已跳过 2 个超出数量限制或读取失败的提交。")
            .contains("为避免模型上下文溢出，已跳过 7 个超出数量、大小限制或读取失败的文件。");
    }
}
