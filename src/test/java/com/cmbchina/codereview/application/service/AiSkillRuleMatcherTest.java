package com.cmbchina.codereview.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.AiSkillEntity;
import org.junit.jupiter.api.Test;

class AiSkillRuleMatcherTest {

    private final AiSkillRuleMatcher matcher = new AiSkillRuleMatcher();

    @Test
    void filtersBySkillProjectType() {
        AiSkillEntity skill = new AiSkillEntity();
        skill.setProjectType("FRONTEND");
        Project project = new Project();
        project.setProjectType("BACKEND");

        assertFalse(matcher.appliesToProject(skill, project));

        project.setProjectType("FRONTEND");
        assertTrue(matcher.appliesToProject(skill, project));
    }

    @Test
    void matchesEnabledRulesAgainstFilePathAndDiffContent() {
        AiSkillEntity skill = new AiSkillEntity();
        skill.setRuleMatchingEnabled(1);
        skill.setMatchRules("ext:java\ncontains:@Transactional");

        DiffChunk javaChunk = new DiffChunk(
            "src/main/java/com/example/UserService.java",
            1,
            1,
            1,
            "@@ -1,1 +1,2 @@\n+@Transactional\n+public void save() {}"
        );
        DiffChunk markdownChunk = new DiffChunk(
            "README.md",
            1,
            1,
            1,
            "@@ -1,1 +1,1 @@\n+documentation"
        );

        assertTrue(matcher.matchesChunk(skill, javaChunk));
        assertFalse(matcher.matchesChunk(skill, markdownChunk));
    }

    @Test
    void disabledRuleMatchingAllowsAnyChunk() {
        AiSkillEntity skill = new AiSkillEntity();
        skill.setRuleMatchingEnabled(0);
        skill.setMatchRules("ext:java");

        DiffChunk chunk = new DiffChunk("package.json", 1, 1, 1, "{}");

        assertTrue(matcher.matchesChunk(skill, chunk));
    }
}
