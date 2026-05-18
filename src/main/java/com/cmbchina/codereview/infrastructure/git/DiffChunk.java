package com.cmbchina.codereview.infrastructure.git;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DiffChunk {

    private String filePath;

    private Integer chunkIndex;

    private Integer newStartLine;

    private Integer oldStartLine;

    private String content;

    public DiffChunk(String filePath, Integer chunkIndex, String content) {
        this.filePath = filePath;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public DiffChunk(String filePath, Integer chunkIndex, Integer oldStartLine, Integer newStartLine, String content) {
        this.filePath = filePath;
        this.chunkIndex = chunkIndex;
        this.oldStartLine = oldStartLine;
        this.newStartLine = newStartLine;
        this.content = content;
    }
}
