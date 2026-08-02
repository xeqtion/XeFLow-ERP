package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.CurrencyRate;
import com.erp.basedata.mapper.CurrencyRateMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 汇率服务 - 维护与换算 (外币 -> 本位币)
 */
public interface CurrencyService extends IService<CurrencyRate> {

    String baseCurrency();

    IPage<CurrencyRate> pageQuery(PageQuery query, LocalDate effectiveDate);

    /** 取换算汇率: 本位币返回 1; 外币取指定日期前最新汇率, 无则抛异常 */
    BigDecimal rate(String currency, LocalDate onDate);

    /** 折算到本位币 (四舍五入到 2 位) */
    BigDecimal convert(BigDecimal amount, String currency, LocalDate onDate);

    /** Convert between any two supported currencies through their base-currency rates. */
    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate onDate);

    @Service
    class Impl extends ServiceImpl<CurrencyRateMapper, CurrencyRate> implements CurrencyService {

        @Value("${erp.currency.base:CNY}")
        private String base;

        @Override
        public String baseCurrency() {
            return base;
        }

        @Override
        public IPage<CurrencyRate> pageQuery(PageQuery query, LocalDate effectiveDate) {
            LambdaQueryWrapper<CurrencyRate> wrapper = new LambdaQueryWrapper<CurrencyRate>()
                    .eq(CurrencyRate::getEffectiveDate,
                            effectiveDate == null ? LocalDate.now() : effectiveDate)
                    .orderByDesc(CurrencyRate::getEffectiveDate)
                    .orderByDesc(CurrencyRate::getId);
            if (StringUtils.hasText(query.getKeyword())) {
                wrapper.like(CurrencyRate::getCurrency, query.getKeyword().trim().toUpperCase());
            }
            return page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        }

        @Override
        public BigDecimal rate(String currency, LocalDate onDate) {
            if (!StringUtils.hasText(currency) || base.equalsIgnoreCase(currency.trim())) {
                return BigDecimal.ONE;
            }
            CurrencyRate r = baseMapper.selectLatest(currency.trim().toUpperCase(), onDate);
            if (r == null || r.getRateToBase() == null) {
                throw new BusinessException("缺少 " + currency + " 的汇率, 请先在汇率管理中维护");
            }
            return r.getRateToBase();
        }

        @Override
        public BigDecimal convert(BigDecimal amount, String currency, LocalDate onDate) {
            if (amount == null) {
                return BigDecimal.ZERO;
            }
            return amount.multiply(rate(currency, onDate)).setScale(2, RoundingMode.HALF_UP);
        }

        @Override
        public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate onDate) {
            if (amount == null) {
                return BigDecimal.ZERO;
            }
            BigDecimal fromRate = rate(fromCurrency, onDate);
            BigDecimal toRate = rate(toCurrency, onDate);
            return amount.multiply(fromRate)
                    .divide(toRate, 4, RoundingMode.HALF_UP);
        }
    }
}
