package com.cmbchina.codereview.infrastructure.git;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiffChunk {

    private String filePath;

    private Integer chunkIndex;

    private String content;
}
