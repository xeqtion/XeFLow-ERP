package com.erp.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.page.PageQuery;
import com.erp.order.entity.ShippingInstruction;

import java.util.List;

public interface ShippingInstructionService extends IService<ShippingInstruction> {

    IPage<ShippingInstruction> pageQuery(PageQuery query);

    List<ShippingInstruction> listByKeyword(String keyword);

    ShippingInstruction getDetail(Long id);
}
