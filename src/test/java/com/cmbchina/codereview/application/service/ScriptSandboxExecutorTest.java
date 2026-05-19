package com.cmbchina.codereview.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScriptSandboxExecutorTest {

    private final ScriptSandboxExecutor executor = new ScriptSandboxExecutor();

    @Test
    void blocksDangerousScriptBeforeStartingProcess() {
        ScriptExecutionRequest request = new ScriptExecutionRequest();
        request.setLanguage("PYTHON");
        request.setContent("import os\nos.system('rm -rf /')\n");

        ScriptExecutionResult result = executor.execute(request);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getSecurityBlocked()).isTrue();
        assertThat(result.getStderr()).contains("受限命令");
    }

    @Test
    void rejectsOversizedInput() {
        ScriptExecutionRequest request = new ScriptExecutionRequest();
        request.setLanguage("NODE");
        request.setContent("console.log('{}')");
        request.setInputJson("x".repeat(500001));

        ScriptExecutionResult result = executor.execute(request);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getSecurityBlocked()).isTrue();
        assertThat(result.getStderr()).contains("脚本输入超过安全上限");
    }
}
