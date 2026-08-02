package com.erp.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OEM 要求附件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_oem_file")
public class OrderOemFile extends BaseEntity {

    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 所属 OEM 要求 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long oemId;

    /** 原始文件名 */
    private String fileName;

    /** 磁盘存储名 */
    private String storedName;

    /** MIME 类型 */
    private String contentType;

    /** 字节大小 */
    private Long size;

    /** 上传人姓名 */
    private String uploaderName;

    /** 备注 */
    private String remark;
}
