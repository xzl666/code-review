package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.common.enums.IssueSource;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.domain.project.Project;
import com.cmbchina.codereview.infrastructure.ai.DeepSeekClient;
import com.cmbchina.codereview.infrastructure.git.DiffChunk;
import com.cmbchina.codereview.infrastructure.persistence.entity.AiSkillEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewIssueEntity;
import com.cmbchina.codereview.infrastructure.persistence.entity.ReviewRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ReviewIssueMapper;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class AiReviewExecutor {

    private final DeepSeekClient deepSeekClient;

    private final ReviewIssueMapper reviewIssueMapper;

    private final ReviewIssuePayloadParser reviewIssuePayloadParser;

    public AiReviewExecutor(DeepSeekClient deepSeekClient,
                            ReviewIssueMapper reviewIssueMapper,
                            ReviewIssuePayloadParser reviewIssuePayloadParser) {
        this.deepSeekClient = deepSeekClient;
        this.reviewIssueMapper = reviewIssueMapper;
        this.reviewIssuePayloadParser = reviewIssuePayloadParser;
    }

    public int execute(Long taskId,
                       Project project,
                       ReviewRuleEntity rule,
                       AiSkillEntity skill,
                       DiffChunk chunk,
                       String branch,
                       Integer reviewDays) {
        String arguments = deepSeekClient.review(project, rule, skill, chunk, branch, reviewDays);
        return saveIssues(taskId, project, rule, skill, chunk, branch, reviewDays, arguments);
    }

    private int saveIssues(Long taskId,
                           Project project,
                           ReviewRuleEntity rule,
                           AiSkillEntity skill,
                           DiffChunk chunk,
                           String branch,
                           Integer reviewDays,
                           String arguments) {
        try {
            return saveParsedIssues(taskId, project, rule, skill, chunk, arguments);
        } catch (Exception firstException) {
            try {
                String repairedArguments = deepSeekClient.repairReviewArguments(
                    arguments,
                    firstException.getMessage(),
                    project,
                    rule,
                    skill,
                    chunk,
                    branch,
                    reviewDays
                );
                return saveParsedIssues(taskId, project, rule, skill, chunk, repairedArguments);
            } catch (Exception secondException) {
                throw new BizException(ErrorCode.BIZ_ERROR, "AI function arguments are not valid review issue JSON after repair: "
                    + secondException.getMessage() + "; original parse error: " + firstException.getMessage());
            }
        }
    }

    private int saveParsedIssues(Long taskId,
                                 Project project,
                                 ReviewRuleEntity rule,
                                 AiSkillEntity skill,
                                 DiffChunk chunk,
                                 String arguments) throws Exception {
            List<ReviewIssueEntity> issues = reviewIssuePayloadParser.parse(
                arguments,
                taskId,
                project,
                rule,
                skill.getId(),
                IssueSource.AI,
                chunk.getFilePath()
            );
            for (ReviewIssueEntity entity : issues) {
                enrichCodeSnippet(entity, chunk);
                reviewIssueMapper.insert(entity);
            }
            return issues.size();
    }

    private void enrichCodeSnippet(ReviewIssueEntity entity, DiffChunk chunk) {
        if (StringUtils.hasText(entity.getCodeSnippet()) || chunk == null || !StringUtils.hasText(chunk.getContent())) {
            return;
        }
        String snippet = snippetFromDiff(chunk, entity.getStartLine(), entity.getEndLine());
        if (StringUtils.hasText(snippet)) {
            entity.setCodeSnippet(snippet);
        }
    }

    private String snippetFromDiff(DiffChunk chunk, Integer startLine, Integer endLine) {
        int targetStart = startLine == null || startLine < 1 ? -1 : startLine;
        int targetEnd = endLine == null || endLine < targetStart ? targetStart : endLine;
        int currentNewLine = chunk.getNewStartLine() == null ? 0 : chunk.getNewStartLine();
        StringBuilder builder = new StringBuilder();
        for (String line : chunk.getContent().split("\\R", -1)) {
            if (line.startsWith("@@")) {
                currentNewLine = parseNewStartLine(line, currentNewLine);
                continue;
            }
            if (line.startsWith("-")) {
                continue;
            }
            int lineNumber = currentNewLine;
            boolean hasNewLineNumber = !line.startsWith("\\ No newline at end of file");
            if (targetStart < 1 || (lineNumber >= targetStart && lineNumber <= targetEnd)) {
                if (builder.length() > 0) {
                    builder.append(System.lineSeparator());
                }
                builder.append(lineNumber > 0 ? lineNumber : "?")
                    .append("  ")
                    .append(line);
            }
            if (hasNewLineNumber) {
                currentNewLine++;
            }
        }
        return builder.toString();
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
        if (number.length() == 0) {
            return fallback;
        }
        return Integer.parseInt(number.toString());
    }
}
