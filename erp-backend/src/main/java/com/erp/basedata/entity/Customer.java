package com.erp.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 客户资料。
 *
 * <p>{@code id} 是系统内部技术主键, 供订单/附件/财务等表关联。
 * {@code custCode} 是界面上的业务客户ID, 按国家 ISO alpha-2 前缀自动生成, 可在详情中修改但必须唯一。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer")
public class Customer extends BaseEntity {

    /** 雪花ID序列化为字符串, 避免前端精度丢失致按 id 操作(删除/附件)失效 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 归属业务员(担当)用户ID, 新建时自动填当前用户 */
    private Long ownerId;

    /** 客户池: PERSONAL 个人发展客户池 / PUBLIC 公共发展客户池。 */
    private String poolType;

    /** 当前担当确认时间，也是个人池无成交回收周期的起算时间。 */
    private LocalDateTime ownerAssignedAt;

    /** 当前担当姓名，仅用于列表及详情展示。 */
    @TableField(exist = false)
    private String ownerName;

    /** 客户ID/编号 (业务主键, 如 CN0001、US0001、ST0001) */
    private String custCode;

    /** 联系人姓名 */
    private String contactName;

    /** 职位 */
    private String contactPosition;

    /** 公司名称 */
    private String companyName;

    /** 网站 */
    private String website;

    /** 社媒联系 */
    private String socialContact;

    /** 客户中文名 */
    private String custNameCn;

    /** 客户英文名 */
    private String custNameEn;

    /** 营业执照号 */
    private String businessLicense;

    /** 税号 */
    private String taxNo;

    /** 开户行 */
    private String bankName;

    /** 银行账户 */
    private String bankAccount;

    /** 联系人 */
    private String contact;

    /** 电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 国家 */
    private String country;

    /** 地址 */
    private String address;

    /** 币种 */
    private String currency;

    /** 信用额度(本位币); 0/空 表示不限 */
    private java.math.BigDecimal creditLimit;

    /** 备注 */
    private String remark;
}
