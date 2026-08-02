package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户附件 - 客户详细资料文件(PDF/图片/表格等), 落磁盘 + 元数据入库
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer_file")
public class CustomerFile extends BaseEntity {

    /** 雪花ID序列化为字符串, 避免前端精度丢失致下载/删除失效 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 所属客户ID(随客户 id 一并字符串化) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 原始文件名 */
    private String fileName;

    /** 磁盘存储名 (UUID) */
    private String storedName;

    /** MIME 类型 */
    private String contentType;

    /** 字节大小 */
    private Long size;

    /** 上传人姓名 (冗余便于展示) */
    private String uploaderName;

    /** 备注 */
    private String remark;
}
