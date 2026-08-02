package com.erp.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.page.PageQuery;
import com.erp.order.entity.OrderOem;

import java.util.List;

public interface OrderOemService extends IService<OrderOem> {

    IPage<OrderOem> pageQuery(PageQuery query);

    List<OrderOem> listByKeyword(String keyword);
}
