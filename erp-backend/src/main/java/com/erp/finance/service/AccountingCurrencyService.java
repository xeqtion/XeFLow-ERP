package com.erp.finance.service;

import com.erp.basedata.service.CurrencyService;
import com.erp.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 财务统计统一使用的本位币折算入口。 */
@Slf4j
@Service
public class AccountingCurrencyService {

    private final CurrencyService currencyService;

    public AccountingCurrencyService(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    public String baseCurrency() {
        return currencyService.baseCurrency();
    }

    /**
     * 按业务发生日折算；周末、节假日由汇率服务取该日期之前最近牌价。
     * 若系统尚无该历史日期之前的牌价，则回退当前最新牌价，但绝不把外币原值直接当作人民币。
     */
    public BigDecimal toBase(BigDecimal amount, String currency, LocalDate businessDate) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        LocalDate date = businessDate != null ? businessDate : LocalDate.now();
        try {
            return currencyService.convert(amount, currency, date);
        } catch (BusinessException historicalMissing) {
            try {
                BigDecimal converted = currencyService.convert(amount, currency, null);
                log.warn("[FX] {} 在 {} 无历史汇率，财务统计回退当前最新牌价", normalized(currency), date);
                return converted;
            } catch (BusinessException latestMissing) {
                throw new BusinessException("无法将 " + normalized(currency) + " 折算为 "
                        + baseCurrency() + "，请先维护汇率");
            }
        }
    }

    private String normalized(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase() : baseCurrency();
    }
}
