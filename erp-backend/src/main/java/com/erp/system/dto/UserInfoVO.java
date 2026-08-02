package com.erp.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Schema(description = "当前登录用户信息")
public class UserInfoVO {

    /** 雪花ID(注册用户)序列化为字符串, 与用户列表 id 比较保持一致 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String username;
    private String realName;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;

    @Schema(description = "角色标识集合")
    private List<String> roles;

    @Schema(description = "权限标识集合, 前端按钮级控制")
    private Set<String> permissions;
}
