package org.example.blogbackend.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private Long page;
    private Long size;  //每页条数
    private Long total;
    private Long pages;
    private List<T> records;  //回显数据记录

    public PageResult() {
    }

    public PageResult(Long page, Long size, Long total, List<T> records) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.pages = (size == null || size == 0) ? 0L : (total + size - 1) / size;
        this.records = records;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }
}
