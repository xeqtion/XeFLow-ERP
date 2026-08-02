package com.erp.common.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 通用分页返回结构
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long pageNum;
    private long pageSize;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return result;
    }
}
