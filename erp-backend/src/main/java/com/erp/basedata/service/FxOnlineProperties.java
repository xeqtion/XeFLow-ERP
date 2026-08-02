package com.erp.basedata.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 在线实时汇率配置 (前缀 erp.currency.online)。
 * 数据源: 中国银行外汇牌价(境内直连可用, 折算到 CNY)。
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.currency.online")
public class FxOnlineProperties {

    /** 是否启用在线汇率(定时刷新) */
    private boolean enabled = true;

    /** 中国银行外汇牌价页面 */
    private String bocUrl = "https://www.boc.cn/sourcedb/whpj/";

    /** 取中行哪一列作为汇率: middle(中行折算价,默认) / buy(现汇买入价) / sell(现汇卖出价) */
    private String bocRateType = "middle";

    /**
     * 出网代理(形如 http://127.0.0.1:7897)。为空时自动读取环境变量 HTTPS_PROXY/HTTP_PROXY;
     * 仍为空则直连。境内服务器拉中行牌价直连即可, 一般无需配置。
     */
    private String proxy = "";

    /** 单次 HTTP 超时(毫秒) */
    private int timeoutMs = 8000;

    /** 需要拉取的外币 */
    private List<String> currencies = new ArrayList<>(
            List.of("USD", "EUR", "HKD", "JPY", "GBP", "SGD", "RUB", "TWD"));

    /** 每日定时刷新 cron, 默认 08:30 */
    private String cron = "0 30 8 * * ?";
}
