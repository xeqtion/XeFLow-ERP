package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 雪花ID序列化为字符串, 防前端 JS Number 精度丢失(注册用户为雪花ID) */
    @Override
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 登录账号 */
    private String username;

    /** 密码 (BCrypt) */
    private String password;

    /** 姓名 */
    private String realName;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 状态: 1 启用, 0 禁用 */
    private Integer status;

    /** 头像 */
    private String avatar;

    /** 备注 */
    private String remark;
}
