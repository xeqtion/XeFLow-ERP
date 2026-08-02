package com.erp.basedata.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Central currency metadata used by Bank of China parsing and UI options. */
public final class CurrencyCatalog {

    private static final Map<String, String> ISO_TO_CHINESE;
    private static final Map<String, String> BOC_NAME_TO_ISO;

    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("CNY", "人民币");
        names.put("USD", "美元");
        names.put("EUR", "欧元");
        names.put("GBP", "英镑");
        names.put("HKD", "港币");
        names.put("JPY", "日元");
        names.put("AUD", "澳大利亚元");
        names.put("CAD", "加拿大元");
        names.put("SGD", "新加坡元");
        names.put("CHF", "瑞士法郎");
        names.put("NZD", "新西兰元");
        names.put("KRW", "韩国元");
        names.put("THB", "泰国铢");
        names.put("MYR", "林吉特");
        names.put("RUB", "卢布");
        names.put("MOP", "澳门元");
        names.put("DKK", "丹麦克朗");
        names.put("NOK", "挪威克朗");
        names.put("SEK", "瑞典克朗");
        names.put("INR", "印度卢比");
        names.put("AED", "阿联酋迪拉姆");
        names.put("TWD", "新台币");
        ISO_TO_CHINESE = Collections.unmodifiableMap(names);

        Map<String, String> bocNames = new LinkedHashMap<>();
        names.forEach((iso, chineseName) -> {
            if (!"CNY".equals(iso)) {
                bocNames.put(chineseName, iso);
            }
        });
        BOC_NAME_TO_ISO = Collections.unmodifiableMap(bocNames);
    }

    private CurrencyCatalog() {
    }

    public static String chineseName(String currency) {
        if (currency == null) {
            return "";
        }
        return ISO_TO_CHINESE.getOrDefault(currency.trim().toUpperCase(), currency.trim().toUpperCase());
    }

    public static String isoByBocName(String bocName) {
        return bocName == null ? null : BOC_NAME_TO_ISO.get(bocName.trim());
    }
}
