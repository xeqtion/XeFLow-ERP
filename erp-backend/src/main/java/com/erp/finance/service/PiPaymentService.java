package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BusinessException;
import com.erp.finance.constant.FinanceType;
import com.erp.finance.entity.FinanceEntry;
import com.erp.finance.mapper.FinanceEntryMapper;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.mapper.SalesPiMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Centralizes PI receipt totals and payment-driven status transitions. */
@Service
public class PiPaymentService {

    private final FinanceEntryMapper financeEntryMapper;
    private final SalesPiMapper salesPiMapper;

    public PiPaymentService(FinanceEntryMapper financeEntryMapper, SalesPiMapper salesPiMapper) {
        this.financeEntryMapper = financeEntryMapper;
        this.salesPiMapper = salesPiMapper;
    }

    public BigDecimal receivedAmount(String piNo) {
        return sum(piNo, null);
    }

    public BigDecimal depositReceivedAmount(String piNo) {
        return sum(piNo, FinanceType.PRE_RECEIVE.name());
    }

    public boolean isFullyPaid(SalesPi pi) {
        BigDecimal total = zero(pi == null ? null : pi.getTotalAmount());
        return pi != null && total.signum() > 0 && receivedAmount(pi.getPiNo()).compareTo(total) >= 0;
    }

    public void assertFullyPaid(SalesPi pi) {
        if (pi == null) {
            throw new BusinessException("PI 不存在");
        }
        BigDecimal total = zero(pi.getTotalAmount());
        BigDecimal received = receivedAmount(pi.getPiNo());
        if (total.signum() <= 0 || received.compareTo(total) < 0) {
            throw new BusinessException("PI 尚未收全款，不能生成出货单。应收 "
                    + total.stripTrailingZeros().toPlainString() + "，已收 "
                    + received.stripTrailingZeros().toPlainString());
        }
    }

    public void syncPiAfterReceipt(SalesPi pi) {
        if (pi == null || pi.getId() == null || "COMPLETED".equals(pi.getStatus()) || "CLOSED".equals(pi.getStatus())
                || "SHIPPED".equals(pi.getStatus())) {
            return;
        }
        BigDecimal required = zero(pi.getDepositAmount());
        BigDecimal receivedDeposit = depositReceivedAmount(pi.getPiNo());
        if (receivedDeposit.signum() > 0 && (required.signum() <= 0 || receivedDeposit.compareTo(required) >= 0)
                && "CONFIRMED".equals(pi.getStatus())) {
            pi.setStatus("ORDERED");
            salesPiMapper.updateById(pi);
        }
    }

    private BigDecimal sum(String piNo, String type) {
        if (piNo == null || piNo.isBlank()) {
            return BigDecimal.ZERO;
        }
        LambdaQueryWrapper<FinanceEntry> wrapper = new LambdaQueryWrapper<FinanceEntry>()
                .eq(FinanceEntry::getRelatedDocType, "PI")
                .eq(FinanceEntry::getRelatedDocNo, piNo)
                .eq(FinanceEntry::getDirection, "in")
                .eq(type != null, FinanceEntry::getType, type);
        return financeEntryMapper.selectList(wrapper).stream()
                .map(FinanceEntry::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
