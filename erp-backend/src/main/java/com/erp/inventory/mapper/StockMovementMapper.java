package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.entity.StockMovement;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockMovementMapper extends BaseMapper<StockMovement> {
}
