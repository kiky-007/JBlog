package org.example.blogbackend.common.result;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private Long page;
    private Long size;
    private Long total;
    private Long pages;
    private List<T> records;

    public PageResult() {
    }

    public PageResult(Long page, Long size, Long total, List<T> records) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.pages = (size == null || size == 0) ? 0L : (total + size - 1) / size;
        this.records = records;
    }
}
