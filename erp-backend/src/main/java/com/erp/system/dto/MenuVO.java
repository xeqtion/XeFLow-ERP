package com.erp.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "菜单树节点")
public class MenuVO {

    private Long id;
    private Long parentId;
    private String name;
    private String permKey;
    private String path;
    private String component;
    private String icon;
    private Integer sort;

    private List<MenuVO> children = new ArrayList<>();
}
