package com.erp.dataimport.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 导入数据行 - data_json 为 列名->值 的 JSON
 */
@Data
@TableName("import_row")
public class ImportRow implements Serializable {

    @TableId
    private Long id;

    private Long batchId;

    /** 原始行号 */
    private Integer rowIndex;

    /** 列名->值 JSON */
    private String dataJson;
}
