package com.erp.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户展示对象 (不含密码, 携带角色)
 */
@Data
@Schema(description = "用户信息")
public class SysUserVO {

    /** 雪花ID(注册用户)序列化为字符串, 防前端精度丢失致删除/改状态打错行 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String realName;
    private String nickname;
    private String email;
    private String phone;
    private Integer status;
    private String avatar;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "角色ID列表")
    private List<Long> roleIds;

    @Schema(description = "角色名称列表")
    private List<String> roleNames;
}
