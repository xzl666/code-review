package com.cmbchina.codereview.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cmbchina.codereview.application.service.SystemConfigAppService;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class DeepSeekClientTest {

    @Test
    void renderedPromptRequiresChineseDescriptionsAndLineRange() throws Exception {
        DeepSeekClient client = new DeepSeekClient(
            new DeepSeekProperties(),
            mock(SystemConfigAppService.class),
            new ObjectMapper()
        );
        Project project = new Project();
        project.setProjectName("代码检视平台");
        project.setProjectType("JAVA");
        ReviewRuleEntity rule = new ReviewRuleEntity();
        rule.setPromptTemplate("Review ${projectName} ${projectType} ${branch} ${reviewDays} ${diffContent}");
        DiffChunk chunk = new DiffChunk("src/App.java", 1, 10, 20, "@@ -10 +20 @@\n+code");
        Method method = DeepSeekClient.class.getDeclaredMethod(
            "renderPrompt",
            String.class,
            Project.class,
            DiffChunk.class,
            String.class,
            Integer.class
        );
        method.setAccessible(true);

        String prompt = (String) method.invoke(client, rule.getPromptTemplate(), project, chunk, "master", 7);

        assertTrue(prompt.contains("请使用中文填写 summary、detail、suggestion 等描述性字段。"));
        assertTrue(prompt.contains("如果能定位行号，请填写 startLine 和 endLine"));
        assertTrue(prompt.contains("File: src/App.java"));
        assertTrue(prompt.contains("OldStartLine: 10"));
        assertTrue(prompt.contains("NewStartLine: 20"));
    }
}
