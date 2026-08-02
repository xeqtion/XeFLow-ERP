package com.erp.sales.dto;

import com.erp.sales.entity.SalesPi;
import com.erp.sales.entity.SalesPiItem;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * PI 主表 + 明细 (用于新建/修改/详情)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesPiDTO extends SalesPi {

    private List<SalesPiItem> items = new ArrayList<>();
}
