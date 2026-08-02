package com.erp.dataimport.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导入批次 - 一次"上传某 xlsx 的某 sheet"对应一个批次
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("import_batch")
public class ImportBatch extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 来源文件名 */
    private String fileName;

    /** sheet 名 */
    private String sheetName;

    /** 列名(表头) JSON 数组 */
    private String columnsJson;

    /** 数据行数 */
    private Integer rowCount;

    /** 导入人姓名 */
    private String importerName;

    /** 备注 */
    private String remark;
}
