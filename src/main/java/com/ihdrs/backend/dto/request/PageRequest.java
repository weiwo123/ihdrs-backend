// PageRequest.java - 分页请求
package com.ihdrs.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "分页查询请求参数")
public class PageRequest {

    @Schema(description = "当前页码", example = "1")
    @Min(value = 1, message = "页码必须大于0")
    private Long current = 1L;

    @Schema(description = "每页数量", example = "10")
    @Min(value = 1, message = "页面大小必须大于0")
    private Long size = 10L;

    @Schema(description = "搜索关键词（用户名、邮箱、电话）")
    private String search;

    @Schema(description = "角色筛选 (ADMIN/USER)")
    private String role;

    @Schema(description = "状态筛选 (true=正常, false=禁用)")
    private Boolean status;

    private String sortField;
    private String sortOrder = "ASC";

    // 计算偏移量
    public Long getOffset() {
        return (current - 1) * size;
    }
}