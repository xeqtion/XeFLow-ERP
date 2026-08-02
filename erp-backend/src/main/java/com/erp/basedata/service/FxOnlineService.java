package com.erp.basedata.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.CurrencyOption;
import com.erp.basedata.entity.CurrencyRate;
import com.erp.basedata.mapper.CurrencyRateMapper;
import com.erp.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在线实时汇率 - 拉取中国银行外汇牌价当日汇率并写入 currency_rate(今日生效),
 * 之后 {@link CurrencyService#rate} 即自动取到最新值; 调用失败回退已维护的手工汇率。
 *
 * <p>数据源: 中国银行外汇牌价(境内直连可用), 仅折算到 CNY。
 */
@Slf4j
@Service
public class FxOnlineService {

    private final CurrencyRateMapper rateMapper;
    private final CurrencyService currencyService;
    private final FxOnlineProperties props;

    /** 中行牌价表行: <tr data-currency='美元'> ... </tr> */
    private static final Pattern BOC_ROW =
            Pattern.compile("<tr[^>]*data-currency='([^']*)'[^>]*>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern BOC_TD =
            Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    public FxOnlineService(CurrencyRateMapper rateMapper, CurrencyService currencyService,
                           FxOnlineProperties props) {
        this.rateMapper = rateMapper;
        this.currencyService = currencyService;
        this.props = props;
    }

    public List<CurrencyOption> currencyOptions() {
        String baseCurrency = currencyService.baseCurrency().trim().toUpperCase();
        Map<String, String> names = new LinkedHashMap<>();
        names.put(baseCurrency, CurrencyCatalog.chineseName(baseCurrency));
        normalizedWantedCurrencies().forEach(code -> names.put(code, CurrencyCatalog.chineseName(code)));
        rateMapper.selectCurrencyCatalog().forEach(rate -> {
            if (!StringUtils.hasText(rate.getCurrency())) {
                return;
            }
            String code = rate.getCurrency().trim().toUpperCase();
            String name = StringUtils.hasText(rate.getCurrencyName())
                    ? rate.getCurrencyName().trim()
                    : CurrencyCatalog.chineseName(code);
            names.put(code, name);
        });
        List<CurrencyOption> options = new java.util.ArrayList<>();
        options.add(new CurrencyOption(baseCurrency, names.remove(baseCurrency), true));
        normalizedWantedCurrencies().forEach(code -> {
            String name = names.remove(code);
            options.add(new CurrencyOption(code,
                    StringUtils.hasText(name) ? name : CurrencyCatalog.chineseName(code), false));
        });
        names.forEach((code, name) -> options.add(new CurrencyOption(code, name, false)));
        return options;
    }

    /** 拉取并写入当日中行汇率, 返回 base/date/rates/failed */
    public Map<String, Object> refresh() {
        String baseCur = currencyService.baseCurrency();
        if (!baseCur.equalsIgnoreCase("CNY")) {
            throw new BusinessException("中国银行外汇牌价仅支持折算到 CNY, 当前本位币=" + baseCur);
        }
        LocalDate today = LocalDate.now();
        Map<String, Object> fetched = new LinkedHashMap<>();
        Map<String, String> failed = new LinkedHashMap<>();

        // 需要的外币(去重、剔除本位币)
        List<String> wanted = normalizedWantedCurrencies();

        Map<String, BigDecimal> bocRates = fetchBocRates();
        for (String c : wanted) {
            BigDecimal rate = bocRates.get(c);
            if (rate != null) {
                upsertToday(c, rate, today);
                fetched.put(c, rate);
            } else {
                failed.put(c, "中行牌价无此币种");
            }
        }

        if (fetched.isEmpty()) {
            throw new BusinessException("在线汇率获取失败(中行牌价无目标币种或无法访问 boc.cn): " + failed);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("base", baseCur);
        result.put("date", today.toString());
        result.put("source", "boc");
        result.put("rates", fetched);
        result.put("failed", failed);
        return result;
    }

    // ---------------- 中国银行外汇牌价 ----------------

    /** 拉取并解析中行牌价页, 返回 ISO 代码 -> 1 外币折合多少 CNY */
    private Map<String, BigDecimal> fetchBocRates() {
        String html = httpGet(props.getBocUrl(), StandardCharsets.UTF_8);
        if (html == null || !html.contains("data-currency")) {
            throw new BusinessException("中行牌价页面格式异常或无法访问");
        }
        int col = bocColumnIndex();          // 取哪一列(原始 td 下标)
        int fallbackCol = bocFallbackIndex(); // 该列为空时的兜底列(现汇->现钞)
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        Matcher rm = BOC_ROW.matcher(html);
        while (rm.find()) {
            String iso = CurrencyCatalog.isoByBocName(rm.group(1));
            if (iso == null) continue;
            List<String> tds = new java.util.ArrayList<>();
            Matcher tm = BOC_TD.matcher(rm.group(2));
            while (tm.find()) {
                tds.add(tm.group(1).replaceAll("<[^>]+>", "").trim());
            }
            // tds: 0=名称 1=现汇买入 2=现钞买入 3=现汇卖出 4=现钞卖出 5=中行折算价 ...
            BigDecimal per100 = pick(tds, col);
            if (per100 == null) per100 = pick(tds, fallbackCol);
            if (per100 == null) continue;
            // 中行报价为每 100 外币单位
            result.put(iso, per100.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        }
        if (result.isEmpty()) {
            throw new BusinessException("中行牌价解析为空");
        }
        return result;
    }

    private BigDecimal pick(List<String> tds, int idx) {
        if (idx < 0 || idx >= tds.size()) return null;
        String v = tds.get(idx);
        if (!StringUtils.hasText(v)) return null;
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int bocColumnIndex() {
        return switch (props.getBocRateType() == null ? "middle" : props.getBocRateType().trim().toLowerCase()) {
            case "buy" -> 1;     // 现汇买入价
            case "sell" -> 3;    // 现汇卖出价
            default -> 5;        // 中行折算价
        };
    }

    private int bocFallbackIndex() {
        return switch (props.getBocRateType() == null ? "middle" : props.getBocRateType().trim().toLowerCase()) {
            case "buy" -> 2;     // 现钞买入价
            case "sell" -> 4;    // 现钞卖出价
            default -> -1;
        };
    }

    // ---------------- 公共 HTTP(支持代理) ----------------

    private String httpGet(String url, java.nio.charset.Charset charset) {
        HttpRequest req = HttpRequest.get(url).timeout(props.getTimeoutMs());
        String[] proxy = resolveProxy();
        if (proxy != null) {
            req.setHttpProxy(proxy[0], Integer.parseInt(proxy[1]));
        }
        try (HttpResponse resp = req.execute()) {
            return new String(resp.bodyBytes(), charset);
        }
    }

    /** 解析代理: 配置 proxy 优先, 否则读环境变量 HTTPS_PROXY/HTTP_PROXY; 返回 [host, port] 或 null */
    private String[] resolveProxy() {
        String p = props.getProxy();
        if (!StringUtils.hasText(p)) {
            p = firstNonBlank(System.getenv("HTTPS_PROXY"), System.getenv("https_proxy"),
                    System.getenv("HTTP_PROXY"), System.getenv("http_proxy"));
        }
        if (!StringUtils.hasText(p)) return null;
        try {
            String u = p.contains("://") ? p : "http://" + p;
            URI uri = URI.create(u.trim());
            int port = uri.getPort() > 0 ? uri.getPort() : 80;
            if (!StringUtils.hasText(uri.getHost())) return null;
            return new String[]{uri.getHost(), String.valueOf(port)};
        } catch (Exception e) {
            log.warn("[FX] 代理配置无法解析: {}", p);
            return null;
        }
    }

    private String firstNonBlank(String... vs) {
        for (String v : vs) {
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }

    private List<String> normalizedWantedCurrencies() {
        String baseCurrency = currencyService.baseCurrency();
        return props.getCurrencies().stream()
                .filter(StringUtils::hasText)
                .map(currency -> currency.trim().toUpperCase())
                .filter(currency -> !baseCurrency.equalsIgnoreCase(currency))
                .distinct()
                .toList();
    }

    // ---------------- 写库 ----------------

    private void upsertToday(String currency, BigDecimal rate, LocalDate today) {
        String remark = "在线实时汇率(中国银行牌价)";
        CurrencyRate exist = rateMapper.selectOne(Wrappers.<CurrencyRate>lambdaQuery()
                .eq(CurrencyRate::getCurrency, currency)
                .eq(CurrencyRate::getEffectiveDate, today).last("LIMIT 1"));
        if (exist != null) {
            exist.setRateToBase(rate);
            if (!StringUtils.hasText(exist.getCurrencyName())) {
                exist.setCurrencyName(CurrencyCatalog.chineseName(currency));
            }
            exist.setRemark(remark);
            rateMapper.updateById(exist);
        } else {
            CurrencyRate r = new CurrencyRate();
            r.setCurrency(currency);
            r.setCurrencyName(CurrencyCatalog.chineseName(currency));
            r.setRateToBase(rate);
            r.setEffectiveDate(today);
            r.setRemark(remark);
            rateMapper.insert(r);
        }
    }

    @Scheduled(cron = "${erp.currency.online.cron:0 30 8 * * ?}")
    public void scheduledRefresh() {
        if (!props.isEnabled()) {
            return;
        }
        try {
            Map<String, Object> r = refresh();
            log.info("[FX] 定时刷新中行汇率: {}", r.get("rates"));
        } catch (Exception e) {
            log.warn("[FX] 定时刷新失败: {}", e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshMissingRatesOnStartup() {
        if (!props.isEnabled()) {
            return;
        }
        List<String> wanted = normalizedWantedCurrencies();
        if (wanted.isEmpty()) {
            return;
        }
        Set<String> existing = rateMapper.selectList(Wrappers.<CurrencyRate>lambdaQuery()
                        .eq(CurrencyRate::getEffectiveDate, LocalDate.now())
                        .in(CurrencyRate::getCurrency, wanted))
                .stream()
                .map(CurrencyRate::getCurrency)
                .collect(Collectors.toSet());
        if (!existing.containsAll(wanted)) {
            scheduledRefresh();
        }
    }
}
