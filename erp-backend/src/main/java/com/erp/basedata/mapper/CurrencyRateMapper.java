package com.erp.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.basedata.entity.CurrencyRate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CurrencyRateMapper extends BaseMapper<CurrencyRate> {

    /** 取某外币在指定日期(含)之前最新生效的汇率 */
    @Select("""
            SELECT * FROM currency_rate
            WHERE deleted = 0 AND currency = #{currency}
              AND (#{onDate} IS NULL OR effective_date <= #{onDate})
            ORDER BY effective_date DESC, id DESC
            LIMIT 1
            """)
    CurrencyRate selectLatest(@Param("currency") String currency, @Param("onDate") LocalDate onDate);

    @Select("""
            SELECT cr.currency, cr.currency_name
            FROM currency_rate cr
            JOIN (
              SELECT currency, MAX(id) AS latest_id
              FROM currency_rate
              WHERE deleted = 0
              GROUP BY currency
            ) latest ON latest.latest_id = cr.id
            WHERE cr.deleted = 0
            ORDER BY cr.currency
            """)
    List<CurrencyRate> selectCurrencyCatalog();
}
