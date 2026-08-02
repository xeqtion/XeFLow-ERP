package com.erp.dataimport.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.basedata.service.CurrencyService;
import com.erp.common.exception.BusinessException;
import com.erp.dataimport.dto.EtlResult;
import com.erp.dataimport.entity.ImportBatch;
import com.erp.dataimport.entity.ImportRow;
import com.erp.dataimport.mapper.ImportBatchMapper;
import com.erp.dataimport.mapper.ImportRowMapper;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.entity.SalesPiItem;
import com.erp.sales.mapper.SalesPiItemMapper;
import com.erp.sales.mapper.SalesPiMapper;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 结构化 ETL - 把"通用导入"得到的 JSON 行(import_batch/import_row)映射进正式业务表,
 * 使数据真正驱动列表/报表/权限, 而非只读 JSON。
 *
 * <p>第一阶段实现: CRM/客户类批次 -> {@code customer} 表。按列名模糊匹配字段, 按公司名去重(可重复执行)。
 */
@Slf4j
@Service
public class EtlService {

    private final ImportBatchMapper batchMapper;
    private final ImportRowMapper rowMapper;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final SalesPiMapper piMapper;
    private final SalesPiItemMapper piItemMapper;
    private final SysUserMapper userMapper;
    private final CurrencyService currencyService;

    public EtlService(ImportBatchMapper batchMapper, ImportRowMapper rowMapper,
                      CustomerMapper customerMapper, ProductMapper productMapper,
                      SalesPiMapper piMapper, SalesPiItemMapper piItemMapper,
                      SysUserMapper userMapper, CurrencyService currencyService) {
        this.batchMapper = batchMapper;
        this.rowMapper = rowMapper;
        this.customerMapper = customerMapper;
        this.productMapper = productMapper;
        this.piMapper = piMapper;
        this.piItemMapper = piItemMapper;
        this.userMapper = userMapper;
        this.currencyService = currencyService;
    }

    private static final String CODE_PREFIX = "CRM";
    private static final String PI_PREFIX = "IMP-PI";
    private static final String ORD_CUST_PREFIX = "ORD";

    /** 把批次中的行映射为客户档案。公司名(列名含"公司")为必填+去重键。 */
    @Transactional(rollbackFor = Exception.class)
    public EtlResult importCustomers(Long batchId) {
        ImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("找不到导入批次: " + batchId);
        }
        List<ImportRow> rows = rowMapper.selectList(Wrappers.<ImportRow>lambdaQuery()
                .eq(ImportRow::getBatchId, batchId).orderByAsc(ImportRow::getRowIndex));

        // 已存在客户英文名(小写) -> 去重
        Set<String> existingNames = new java.util.HashSet<>();
        for (Customer c : customerMapper.selectList(Wrappers.<Customer>lambdaQuery()
                .select(Customer::getCustNameEn))) {
            if (StringUtils.hasText(c.getCustNameEn())) {
                existingNames.add(c.getCustNameEn().trim().toLowerCase());
            }
        }
        // 已用编码集合 + 当前最大 CRM 序号
        Set<String> usedCodes = new TreeSet<>();
        int maxSeq = 0;
        for (Customer c : customerMapper.selectList(Wrappers.<Customer>lambdaQuery()
                .select(Customer::getCustCode))) {
            if (StringUtils.hasText(c.getCustCode())) {
                usedCodes.add(c.getCustCode());
                maxSeq = Math.max(maxSeq, parseSeq(CODE_PREFIX, c.getCustCode()));
            }
        }

        EtlResult r = new EtlResult();
        r.setTarget("customer");
        r.setTotal(rows.size());

        for (ImportRow ir : rows) {
            Map<String, String> row = parseRow(ir.getDataJson());
            String company = pick(row, "公司", "company", "客户名称", "客户");
            if (!StringUtils.hasText(company)) {
                r.setSkippedBlank(r.getSkippedBlank() + 1);
                continue;
            }
            String key = company.trim().toLowerCase();
            if (existingNames.contains(key)) {
                r.setSkippedDuplicate(r.getSkippedDuplicate() + 1);
                continue;
            }

            Customer c = new Customer();
            // 按列宽截断, 避免真实脏数据(如手机字段拼多个号码)触发 Data too long 致整批回滚
            c.setCustNameEn(trunc(company.trim(), 255));
            c.setContact(trunc(pick(row, "姓名", "联系人", "name", "contact"), 64));
            c.setCountry(trunc(pick(row, "国家", "country", "区域"), 64));
            c.setPhone(trunc(pick(row, "手机", "电话", "phone", "tel"), 64));
            c.setEmail(trunc(pick(row, "mail", "邮箱", "邮件"), 128));
            c.setCurrency("USD");
            c.setRemark(trunc(buildRemark(row), 512));

            String code = nextCode(CODE_PREFIX, ++maxSeq, usedCodes);
            c.setCustCode(code);
            usedCodes.add(code);
            customerMapper.insert(c);

            existingNames.add(key);
            r.setCreated(r.getCreated() + 1);
            if (r.getSamples().size() < 8) {
                r.getSamples().add(code + " " + company.trim());
            }
        }
        log.info("[ETL] 批次{} -> customer: 总{} 建{} 空{} 重{}",
                batchId, r.getTotal(), r.getCreated(), r.getSkippedBlank(), r.getSkippedDuplicate());
        return r;
    }

    /** 把批次中的行映射为产品档案。工厂产品型号(prodCode)为必填+去重键。 */
    @Transactional(rollbackFor = Exception.class)
    public EtlResult importProducts(Long batchId) {
        ImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("找不到导入批次: " + batchId);
        }
        List<ImportRow> rows = rowMapper.selectList(Wrappers.<ImportRow>lambdaQuery()
                .eq(ImportRow::getBatchId, batchId).orderByAsc(ImportRow::getRowIndex));

        // 已存在产品代码(小写) -> 去重(prod_code 唯一)
        Set<String> existingCodes = new java.util.HashSet<>();
        for (Product p : productMapper.selectList(Wrappers.<Product>lambdaQuery().select(Product::getProdCode))) {
            if (StringUtils.hasText(p.getProdCode())) {
                existingCodes.add(p.getProdCode().trim().toLowerCase());
            }
        }

        EtlResult r = new EtlResult();
        r.setTarget("product");
        r.setTotal(rows.size());
        long nextProductId = productMapper.selectNextProductId();

        for (ImportRow ir : rows) {
            Map<String, String> row = parseRow(ir.getDataJson());
            String code = pick(row, "工厂产品型号", "工厂型号", "产品型号", "型号");
            if (!StringUtils.hasText(code)) {
                r.setSkippedBlank(r.getSkippedBlank() + 1);
                continue;
            }
            code = code.trim();
            String key = code.toLowerCase();
            if (existingCodes.contains(key)) {
                r.setSkippedDuplicate(r.getSkippedDuplicate() + 1);
                continue;
            }

            Product p = new Product();
            p.setId(nextProductId++);
            p.setProdCode(trunc(code, 64));
            p.setFactoryModel(trunc(code, 255));
            p.setProdNameCn(trunc(pick(row, "产品名称", "名称", "品名"), 255));
            p.setProductName(p.getProdNameCn());
            p.setSpec(trunc(pick(row, "高宇型号", "高宇", "高义型号", "规格"), 255));
            p.setCompanyModel(p.getSpec());
            BigDecimal price = parseDecimal(pick(row, "出厂价", "成本价", "成本", "单价"));
            p.setCostPrice(price);
            p.setFactoryPrice(price);
            p.setCurrency("CNY");
            p.setRemark(trunc(pick(row, "产品备注", "备注", "remark"), 512));
            productMapper.insert(p);

            existingCodes.add(key);
            r.setCreated(r.getCreated() + 1);
            if (r.getSamples().size() < 8) {
                r.getSamples().add(code + " " + (p.getProdNameCn() == null ? "" : p.getProdNameCn()));
            }
        }
        log.info("[ETL] 批次{} -> product: 总{} 建{} 空{} 重{}",
                batchId, r.getTotal(), r.getCreated(), r.getSkippedBlank(), r.getSkippedDuplicate());
        return r;
    }

    /**
     * 把"外贸业绩报告"类批次映射为销售合同 PI(主+明细)。
     * 按 (客户名称+订单日期) 分组成 PI; 客户名称解析/新建客户(满足非空外键);
     * 担当→sys_user 回填 owner_id(匹配不到则归当前用户); 金额=数量×单价(规避源表"金额"重名列丢值);
     * 币种 USD, 用 CurrencyService 折算本位币合计(历史日期无汇率则回退最新)。按 客户+日期 去重→幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public EtlResult importPis(Long batchId) {
        ImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("找不到导入批次: " + batchId);
        }
        List<ImportRow> rows = rowMapper.selectList(Wrappers.<ImportRow>lambdaQuery()
                .eq(ImportRow::getBatchId, batchId).orderByAsc(ImportRow::getRowIndex));

        // 客户名(小写)->id; 产品代码(小写)->id; 担当名(小写)->userId; 已存在 PI 的 客户|日期
        Map<String, Long> custByName = new java.util.HashMap<>();
        Set<String> usedCustCodes = new TreeSet<>();
        int maxCustSeq = 0;
        for (Customer c : customerMapper.selectList(Wrappers.<Customer>lambdaQuery()
                .select(Customer::getId, Customer::getCustNameEn, Customer::getCustCode))) {
            if (StringUtils.hasText(c.getCustNameEn())) {
                custByName.putIfAbsent(c.getCustNameEn().trim().toLowerCase(), c.getId());
            }
            if (StringUtils.hasText(c.getCustCode())) {
                usedCustCodes.add(c.getCustCode());
                maxCustSeq = Math.max(maxCustSeq, parseSeq(ORD_CUST_PREFIX, c.getCustCode()));
            }
        }
        Map<String, Product> productByCode = new java.util.HashMap<>();
        for (Product p : productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .select(Product::getId, Product::getProdCode, Product::getProductName, Product::getCompanyModel,
                        Product::getFactoryModel, Product::getUnit, Product::getProdNameCn, Product::getProdNameEn,
                        Product::getSpec))) {
            if (StringUtils.hasText(p.getProdCode())) {
                productByCode.putIfAbsent(p.getProdCode().trim().toLowerCase(), p);
            }
        }
        Map<String, Long> userByName = new java.util.HashMap<>();
        for (SysUser u : userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getRealName, SysUser::getNickname))) {
            putUser(userByName, u.getRealName(), u.getId());
            putUser(userByName, u.getNickname(), u.getId());
            putUser(userByName, u.getUsername(), u.getId());
        }
        Set<String> existingPiKeys = new java.util.HashSet<>();
        Set<String> usedPiNos = new TreeSet<>();
        int maxPiSeq = 0;
        for (SalesPi p : piMapper.selectList(Wrappers.<SalesPi>lambdaQuery()
                .select(SalesPi::getPiNo, SalesPi::getCustomerId, SalesPi::getSignDate))) {
            if (p.getCustomerId() != null && p.getSignDate() != null) {
                existingPiKeys.add(p.getCustomerId() + "|" + p.getSignDate());
            }
            if (StringUtils.hasText(p.getPiNo())) {
                usedPiNos.add(p.getPiNo());
                maxPiSeq = Math.max(maxPiSeq, parseSeq(PI_PREFIX, p.getPiNo()));
            }
        }

        // 按 客户名称|订单日期 分组(保序)
        Map<String, List<Map<String, String>>> groups = new LinkedHashMap<>();
        Map<String, String> groupCust = new java.util.HashMap<>();
        Map<String, LocalDate> groupDate = new java.util.HashMap<>();
        Map<String, String> groupExtra = new java.util.HashMap<>(); // 担当/区域

        EtlResult r = new EtlResult();
        r.setTarget("sales_pi");
        r.setTotal(rows.size());
        int skippedBlank = 0;

        for (ImportRow ir : rows) {
            Map<String, String> row = parseRow(ir.getDataJson());
            String cust = pick(row, "客户名称", "客户");
            LocalDate date = parseDate(pick(row, "订单日期", "日期"));
            BigDecimal qty = parseDecimal(pick(row, "数量"));
            BigDecimal price = parseDecimal(pick(row, "单价"));
            if (!StringUtils.hasText(cust) || date == null || qty == null || price == null) {
                skippedBlank++;
                continue;
            }
            String gk = cust.trim().toLowerCase() + "|" + date;
            groups.computeIfAbsent(gk, k -> new java.util.ArrayList<>()).add(row);
            groupCust.putIfAbsent(gk, cust.trim());
            groupDate.putIfAbsent(gk, date);
            if (!groupExtra.containsKey(gk)) {
                groupExtra.put(gk, "担当:" + nz(pick(row, "担当")) + " 区域:" + nz(pick(row, "客户区域", "区域")));
            }
        }
        r.setSkippedBlank(skippedBlank);

        int createdPis = 0, createdCusts = 0, matchedOwners = 0, skippedDup = 0;
        BigDecimal baseTotal = BigDecimal.ZERO;
        boolean baseOk = true;

        for (Map.Entry<String, List<Map<String, String>>> g : groups.entrySet()) {
            String gk = g.getKey();
            String custName = groupCust.get(gk);
            LocalDate signDate = groupDate.get(gk);

            // 解析/新建客户
            String custKey = custName.toLowerCase();
            Long custId = custByName.get(custKey);
            if (custId == null) {
                Customer nc = new Customer();
                nc.setCustNameEn(trunc(custName, 255));
                nc.setCountry(trunc(pick(g.getValue().get(0), "客户区域", "区域", "国家"), 64));
                nc.setCurrency("USD");
                nc.setCustCode(nextCode(ORD_CUST_PREFIX, ++maxCustSeq, usedCustCodes));
                usedCustCodes.add(nc.getCustCode());
                customerMapper.insert(nc);
                custId = nc.getId();
                custByName.put(custKey, custId);
                createdCusts++;
            }

            // 幂等: 同客户同订单日期已存在 PI 则跳过整组
            String piKey = custId + "|" + signDate;
            if (existingPiKeys.contains(piKey)) {
                skippedDup += g.getValue().size();
                continue;
            }

            // 担当 -> owner
            String salesman = pick(g.getValue().get(0), "担当");
            Long ownerId = salesman == null ? null : userByName.get(salesman.trim().toLowerCase());
            if (ownerId != null) {
                matchedOwners++;
            }

            SalesPi pi = new SalesPi();
            String piNo = nextCode(PI_PREFIX, ++maxPiSeq, usedPiNos);
            usedPiNos.add(piNo);
            pi.setPiNo(piNo);
            pi.setCustomerId(custId);
            pi.setCurrency("USD");
            pi.setStatus("CONFIRMED");
            pi.setSignDate(signDate);
            pi.setOwnerId(ownerId); // 为 null 时 MetaObjectHandler 回退当前用户
            pi.setRemark(trunc("源自外贸业绩报告; " + groupExtra.get(gk), 512));
            pi.setTotalAmount(BigDecimal.ZERO);
            piMapper.insert(pi);

            BigDecimal piTotal = BigDecimal.ZERO;
            for (Map<String, String> row : g.getValue()) {
                BigDecimal qty = parseDecimal(pick(row, "数量"));
                BigDecimal price = parseDecimal(pick(row, "单价"));
                if (qty == null || price == null) {
                    continue;
                }
                BigDecimal amount = qty.multiply(price);
                SalesPiItem item = new SalesPiItem();
                item.setPiId(pi.getId());
                String model = pick(row, "model", "工厂型号", "型号");
                Product product = model == null ? null : productByCode.get(model.trim().toLowerCase());
                if (product != null) {
                    item.setProductId(product.getId());
                    item.setProdCode(trunc(firstText(product.getProdCode(), model), 64));
                    item.setProductName(trunc(firstText(product.getProductName(), product.getProdNameCn(), product.getProdNameEn()), 255));
                    item.setProductModel(trunc(firstText(product.getCompanyModel(), product.getProdCode(), product.getSpec(), model), 255));
                    item.setFactoryModel(trunc(product.getFactoryModel(), 255));
                    item.setUnit(trunc(quantityUnit(product.getUnit()), 32));
                } else {
                    item.setProdCode(trunc(model, 64));
                    item.setProductModel(trunc(model, 255));
                }
                item.setQty(qty);
                item.setUnitPrice(price);
                item.setAmount(amount);
                piItemMapper.insert(item);
                piTotal = piTotal.add(amount);
            }
            pi.setTotalAmount(piTotal);
            piMapper.updateById(pi);
            existingPiKeys.add(piKey);
            createdPis++;

            // 折算本位币合计(USD->base); 历史日期无汇率则回退最新, 仍无则标记
            if (baseOk) {
                try {
                    baseTotal = baseTotal.add(currencyService.convert(piTotal, "USD", signDate));
                } catch (Exception e1) {
                    try {
                        baseTotal = baseTotal.add(currencyService.convert(piTotal, "USD", LocalDate.now()));
                    } catch (Exception e2) {
                        baseOk = false;
                    }
                }
            }
            if (r.getSamples().size() < 8) {
                r.getSamples().add(piNo + " " + custName + " $" + piTotal.stripTrailingZeros().toPlainString());
            }
        }

        r.setCreated(createdPis);
        r.setSkippedDuplicate(skippedDup);
        String base = currencyService.baseCurrency();
        r.setNote("新建PI " + createdPis + " 张; 新建客户 " + createdCusts + "; 担当匹配 " + matchedOwners
                + " 张; 折合本位币合计 " + (baseOk ? "≈ " + baseTotal.stripTrailingZeros().toPlainString() + " " + base : "(部分缺历史汇率)"));
        log.info("[ETL] 批次{} -> sales_pi: 行{} PI{} 客户{} 跳空{} 跳重{} {}",
                batchId, r.getTotal(), createdPis, createdCusts, skippedBlank, skippedDup, r.getNote());
        return r;
    }

    // ---------------- 私有 ----------------

    private void putUser(Map<String, Long> map, String name, Long id) {
        if (StringUtils.hasText(name)) {
            map.putIfAbsent(name.trim().toLowerCase(), id);
        }
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    /** 解析日期: 取空格前 token, 接受 yyyy-M-d / yyyy/M/d; 失败返回 null */
    private LocalDate parseDate(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        String s = v.trim().split("\\s+")[0].replace('/', '-');
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-M-d"));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> parseRow(String json) {
        Map<String, String> m = new LinkedHashMap<>();
        if (!StringUtils.hasText(json)) {
            return m;
        }
        JSONObject obj = JSONUtil.parseObj(json);
        for (String k : obj.keySet()) {
            Object v = obj.get(k);
            m.put(k, v == null ? "" : String.valueOf(v));
        }
        return m;
    }

    /** 按列名关键字(忽略大小写, contains)取首个非空值; keyword 顺序即优先级 */
    private String pick(Map<String, String> row, String... keywords) {
        for (String kw : keywords) {
            String low = kw.toLowerCase();
            for (Map.Entry<String, String> e : row.entrySet()) {
                if (e.getKey() != null && e.getKey().toLowerCase().contains(low)
                        && StringUtils.hasText(e.getValue())) {
                    return e.getValue().trim();
                }
            }
        }
        return null;
    }

    /** 把无对应字段但有价值的列拼进备注 */
    private String buildRemark(Map<String, String> row) {
        StringBuilder sb = new StringBuilder();
        appendIf(sb, "职位", pick(row, "职位"));
        appendIf(sb, "网站", pick(row, "网站", "website"));
        appendIf(sb, "Skype", pick(row, "skype"));
        appendIf(sb, "来源", pick(row, "from", "来源"));
        appendIf(sb, "标记", pick(row, "mark"));
        appendIf(sb, "备注", pick(row, "备注", "remark"));
        String s = sb.toString();
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    /** 从形如 "410"、"1,200"、"￥650/台" 中提取数值; 无法解析返回 null */
    private java.math.BigDecimal parseDecimal(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        String cleaned = v.replaceAll("[^0-9.\\-]", "");
        // 去掉可能多出的尾随小数点 / 仅符号
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
            return null;
        }
        try {
            return new java.math.BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trunc(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() > max ? v.substring(0, max) : v;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String quantityUnit(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.matches("(?i)USD|CNY|EUR|GBP|JPY|HKD|RMB") ? null : text;
    }

    private void appendIf(StringBuilder sb, String label, String val) {
        if (StringUtils.hasText(val)) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(label).append(": ").append(val.trim());
        }
    }

    /** 生成唯一编码 {prefix}-0001; 若占用则递增 */
    private String nextCode(String prefix, int seq, Set<String> used) {
        String code;
        do {
            code = String.format("%s-%04d", prefix, seq++);
        } while (used.contains(code));
        return code;
    }

    /** 从 {prefix}-0007 解析出 7; 非该前缀返回 0 */
    private int parseSeq(String prefix, String code) {
        if (code == null || !code.startsWith(prefix + "-")) {
            return 0;
        }
        try {
            return Integer.parseInt(code.substring(prefix.length() + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
