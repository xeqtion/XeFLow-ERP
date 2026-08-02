package com.erp.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/修改 入参。id 为空表示新增。
 * 新增时 password 必填; 修改时 password 留空表示不改密。
 */
@Data
@Schema(description = "用户保存入参")
public class SysUserSaveDTO {

    @Schema(description = "用户ID, 空=新增")
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 32, message = "用户名长度需 2-32 位")
    private String username;

    @Size(min = 6, max = 32, message = "密码长度需 6-32 位")
    @Schema(description = "密码; 新增必填, 修改留空=不改密")
    private String password;

    private String realName;

    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    @Schema(description = "状态 1启用 0禁用", example = "1")
    private Integer status = 1;

    private String remark;

    @Schema(description = "角色ID列表")
    private List<Long> roleIds;
}
