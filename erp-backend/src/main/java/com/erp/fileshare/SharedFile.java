package com.erp.fileshare;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 共享文件 - 公司内部资料 (上传人=create_by, 上传时间=create_time)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shared_file")
public class SharedFile extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失致按 id 操作失效 */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 原始文件名 */
    private String fileName;

    /** 磁盘存储名 (UUID) */
    private String storedName;

    /** MIME 类型 */
    private String contentType;

    /** 文件格式/后缀, 如 PDF/XLSX/PNG */
    private String fileFormat;

    /** 字节大小 */
    private Long size;

    /** 分类, 如 制度/模板/认证 */
    private String category;

    /** 上传人姓名 (冗余便于展示) */
    private String uploaderName;

    /** 备注 */
    private String remark;

    /** 过期时间, 到期后自动清理 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
}
