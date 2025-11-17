// PageResult.java
package com.ihdrs.backend.common;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {

    private List<T> list;
    private Long total;
    private Long size;
    private Long current;
    private Long pages;

    /**
     * 静态工厂方法
     */
    public static <T> PageResult<T> of(List<T> list, Long total, Long size, Long current) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);       // ✅ 使用 setList
        result.setTotal(total);
        result.setSize(size);
        result.setCurrent(current);
        // 计算总页数
        result.setPages((total + size - 1) / size);
        return result;
    }

    /**
     * 空结果
     */
    public static <T> PageResult<T> empty() {
        return PageResult.of(List.of(), 0L, 10L, 1L);
    }
}