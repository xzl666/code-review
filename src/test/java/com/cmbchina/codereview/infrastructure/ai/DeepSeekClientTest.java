package com.cmbchina.codereview.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cmbchina.codereview.application.service.SystemConfigAppService;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class DeepSeekClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void renderedPromptRequiresChineseDescriptionsLineRangeAndStrictJson() throws Exception {
        DeepSeekClient client = client();
        Project project = new Project();
        project.setProjectName("code-review");
        project.setProjectType("JAVA");
        ReviewRuleEntity rule = new ReviewRuleEntity();
        rule.setPromptTemplate("Review ${projectName} ${projectType} ${branch} ${reviewDays} ${diffContent}");
        DiffChunk chunk = new DiffChunk("src/App.java", 1, 10, 20, "@@ -10 +20 @@\n+@Valid code");
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

        assertTrue(prompt.contains("Use Chinese for summary, detail, suggestion"));
        assertTrue(prompt.contains("Return only through the provided function"));
        assertTrue(prompt.contains("annotations such as @Valid"));
        assertTrue(prompt.contains("startLine and endLine"));
        assertTrue(prompt.contains("File: src/App.java"));
        assertTrue(prompt.contains("OldStartLine: 10"));
        assertTrue(prompt.contains("NewStartLine: 20"));
    }

    @Test
    void extractArgumentsKeepsObjectArgumentsAsJson() throws Exception {
        DeepSeekClient client = client();
        Method method = DeepSeekClient.class.getDeclaredMethod("extractArguments", String.class);
        method.setAccessible(true);
        String responseBody = "{"
            + "\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"arguments\":{\"issues\":[]}}}]}}]"
            + "}";

        String arguments = (String) method.invoke(client, responseBody);

        assertEquals("{\"issues\":[]}", arguments);
    }

    @Test
    void strictParametersSchemaAddsAdditionalPropertiesFalseToObjects() throws Exception {
        DeepSeekClient client = client();
        Method method = DeepSeekClient.class.getDeclaredMethod("strictParametersSchema", JsonNode.class);
        method.setAccessible(true);
        JsonNode schema = objectMapper.readTree("{"
            + "\"type\":\"object\","
            + "\"properties\":{\"issues\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"summary\":{\"type\":\"string\"}}}}}"
            + "}");

        JsonNode strictSchema = (JsonNode) method.invoke(client, schema);

        assertTrue(strictSchema.path("additionalProperties").isBoolean());
        assertEquals(false, strictSchema.path("additionalProperties").asBoolean());
        assertEquals(false, strictSchema.path("properties").path("issues").path("items").path("additionalProperties").asBoolean());
    }

    private DeepSeekClient client() {
        return new DeepSeekClient(
            new DeepSeekProperties(),
            mock(SystemConfigAppService.class),
            objectMapper
        );
    }
}
