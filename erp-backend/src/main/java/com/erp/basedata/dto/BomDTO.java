package com.erp.basedata.dto;

import com.erp.basedata.entity.Bom;
import com.erp.basedata.entity.BomItem;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * BOM 主表 + 明细 (用于新建/修改/详情)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BomDTO extends Bom {

    private List<BomItem> items = new ArrayList<>();
}
