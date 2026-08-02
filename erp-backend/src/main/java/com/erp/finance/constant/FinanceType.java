package com.erp.finance.constant;

/**
 * 收付款 6 类类型及默认收付方向 (in=收款进账 / out=付款出账)
 */
public enum FinanceType {

    RECEIVABLE("应收", "in"),
    PAYABLE("应付", "out"),
    PRE_RECEIVE("预收定金", "in"),
    PRE_PAY("预付定金", "out"),
    UNRECEIVED("应收未收", "in"),
    UNPAID("应付未付", "out");

    private final String label;
    private final String direction;

    FinanceType(String label, String direction) {
        this.label = label;
        this.direction = direction;
    }

    public String getLabel() {
        return label;
    }

    public String getDirection() {
        return direction;
    }

    /** 解析, 非法返回 null */
    public static FinanceType of(String code) {
        if (code == null) {
            return null;
        }
        for (FinanceType t : values()) {
            if (t.name().equals(code)) {
                return t;
            }
        }
        return null;
    }
}
