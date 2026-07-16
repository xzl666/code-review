package com.cmbchina.codereview.interfaces.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class ProjectCommitResponse {

    private String hash;

    private String shortHash;

    private String subject;

    private String author;

    private String commitTime;

    private List<String> parentHashes;
}
