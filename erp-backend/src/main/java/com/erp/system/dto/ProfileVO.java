package com.erp.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
public class ProfileVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String realName;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    private List<String> roles;

    private List<String> roleNames;
}
