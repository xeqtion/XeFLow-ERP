package com.erp.common.page;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通用分页查询参数
 */
@Data
public class PageQuery {

    @Schema(description = "页码, 从 1 开始", example = "1")
    private long pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private long pageSize = 10;

    @Schema(description = "关键字(模糊查询)")
    private String keyword;
}
