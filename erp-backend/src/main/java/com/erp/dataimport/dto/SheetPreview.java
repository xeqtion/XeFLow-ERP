package com.erp.dataimport.dto;

import lombok.Data;

import java.util.List;

/**
 * sheet 预览 (供选择 sheet 与表头行)
 */
@Data
public class SheetPreview {
    private String sheetName;
    private int rowCount;
    private int colCount;
    /** 前若干行原始内容 (供前端挑选表头行) */
    private List<List<String>> sample;
}
