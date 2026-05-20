package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.AiSkillEntity;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiSkillRuleMatcher {

    public boolean appliesToProject(AiSkillEntity skill, Project project) {
        String skillProjectType = normalize(skill == null ? null : skill.getProjectType(), "ALL");
        String projectType = normalize(project == null ? null : project.getProjectType(), "");
        return "ALL".equals(skillProjectType) || skillProjectType.equals(projectType);
    }

    public boolean matchesChunk(AiSkillEntity skill, DiffChunk chunk) {
        if (skill == null || skill.getRuleMatchingEnabled() == null || skill.getRuleMatchingEnabled() != 1) {
            return true;
        }
        if (!StringUtils.hasText(skill.getMatchRules())) {
            return true;
        }
        String filePath = normalizePath(chunk == null ? "" : chunk.getFilePath());
        String content = chunk == null || chunk.getContent() == null ? "" : chunk.getContent();
        String haystack = (filePath + "\n" + content).toLowerCase(Locale.ROOT);
        for (String rawRule : skill.getMatchRules().split("\\R")) {
            String rule = stripComment(rawRule);
            if (!StringUtils.hasText(rule)) {
                continue;
            }
            if (matchesRule(rule.trim(), filePath, content, haystack)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRule(String rule, String filePath, String content, String haystack) {
        String lowerRule = rule.toLowerCase(Locale.ROOT);
        if (lowerRule.startsWith("ext:")) {
            String extensionText = rule.substring(4);
            for (String extension : extensionText.split(",")) {
                String normalized = extension.trim().toLowerCase(Locale.ROOT);
                if (!normalized.startsWith(".")) {
                    normalized = "." + normalized;
                }
                if (filePath.toLowerCase(Locale.ROOT).endsWith(normalized)) {
                    return true;
                }
            }
            return false;
        }
        if (lowerRule.startsWith("path:") || lowerRule.startsWith("file:")) {
            String pattern = rule.substring(rule.indexOf(':') + 1).trim();
            return globMatches(pattern, filePath);
        }
        if (lowerRule.startsWith("contains:")) {
            String needle = rule.substring("contains:".length()).trim().toLowerCase(Locale.ROOT);
            return StringUtils.hasText(needle) && haystack.contains(needle);
        }
        if (lowerRule.startsWith("regex:")) {
            String pattern = rule.substring("regex:".length()).trim();
            return StringUtils.hasText(pattern) && Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(filePath + "\n" + content)
                .find();
        }
        return haystack.contains(lowerRule);
    }

    private boolean globMatches(String pattern, String filePath) {
        if (!StringUtils.hasText(pattern)) {
            return false;
        }
        return Pattern.compile(globToRegex(normalizePath(pattern)), Pattern.CASE_INSENSITIVE).matcher(filePath).matches();
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char current = glob.charAt(index);
            if (current == '*') {
                boolean doubleStar = index + 1 < glob.length() && glob.charAt(index + 1) == '*';
                regex.append(doubleStar ? ".*" : "[^/]*");
                if (doubleStar) {
                    index++;
                }
            } else if (current == '?') {
                regex.append("[^/]");
            } else {
                if (".[]{}()+-^$|\\".indexOf(current) >= 0) {
                    regex.append('\\');
                }
                regex.append(current);
            }
        }
        regex.append('$');
        return regex.toString();
    }

    private String stripComment(String value) {
        int commentIndex = value.indexOf('#');
        return commentIndex >= 0 ? value.substring(0, commentIndex) : value;
    }

    private String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    private String normalize(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
    }
}
