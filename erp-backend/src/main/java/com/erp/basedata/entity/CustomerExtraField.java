package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户详情中的自定义键值资料, 如银行账户、付款偏好、社媒账号等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer_extra_field")
public class CustomerExtraField extends BaseEntity {

    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    private String fieldKey;

    private String fieldValue;

    private Integer sort;
}
