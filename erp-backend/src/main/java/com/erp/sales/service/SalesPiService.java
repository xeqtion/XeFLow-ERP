package com.erp.sales.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.page.PageQuery;
import com.erp.sales.dto.SalesPiDTO;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.entity.SalesPiItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SalesPiService extends IService<SalesPi> {

    IPage<SalesPi> pageQuery(PageQuery query, Long ownerId);

    SalesPiDTO getDetail(Long id);

    Long savePi(SalesPiDTO dto);

    void updatePi(SalesPiDTO dto);

    void removePi(Long id);

    List<SalesPiItem> listItems(Long piId);

    /** 确认 PI (DRAFT -> CONFIRMED) */
    void confirm(Long id);

    /** 由 PI 一键生成出货单(校验累计出货量不超 PI 数量); qty 为空则取剩余量。返回出货单 id */
    Long generateShipment(Long piId, BigDecimal qty, LocalDate shipDate);
}
