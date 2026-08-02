package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.FinanceEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FinanceEntryMapper extends BaseMapper<FinanceEntry> {
}
