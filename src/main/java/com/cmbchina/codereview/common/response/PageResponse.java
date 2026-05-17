package com.cmbchina.codereview.common.response;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> records;

    private long total;

    private long pageNo;

    private long pageSize;

    public static <T> PageResponse<T> empty(long pageNo, long pageSize) {
        return new PageResponse<>(Collections.emptyList(), 0L, pageNo, pageSize);
    }
}
