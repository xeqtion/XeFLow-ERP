package com.erp.dataimport.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化 ETL 结果 - 把导入批次的 JSON 行落地为正式业务表的统计。
 */
@Data
public class EtlResult {

    /** 目标(如 customer) */
    private String target;

    /** 数据行总数 */
    private int total;

    /** 新建数 */
    private int created;

    /** 跳过-关键字段为空 */
    private int skippedBlank;

    /** 跳过-已存在(重复) */
    private int skippedDuplicate;

    /** 补充说明(如 PI 场景: 客户新建数 / 折合本位币合计 / 担当匹配数) */
    private String note;

    /** 新建样例(前若干条, 形如 "编码 名称") */
    private List<String> samples = new ArrayList<>();
}
