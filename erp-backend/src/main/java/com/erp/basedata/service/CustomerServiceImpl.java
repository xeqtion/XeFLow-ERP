package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.dto.CustomerExcel;
import com.erp.basedata.dto.CustomerAssigneeOption;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.entity.CustomerExtraField;
import com.erp.basedata.mapper.CustomerExtraFieldMapper;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.common.excel.ExcelUtil;
import com.erp.common.excel.ExcelImportValidator;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.security.DataScopeHelper;
import com.erp.common.security.SecurityUtils;
import com.erp.notification.constant.NotificationType;
import com.erp.notification.service.NotificationService;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    private static final String PERSONAL_POOL = "PERSONAL";
    private static final String PUBLIC_POOL = "PUBLIC";
    private static final int CUSTOMER_CODE_MAX_LENGTH = 64;
    private static final int CUSTOMER_PHONE_MAX_LENGTH = 255;
    private static final String DELETED_CODE_SEPARATOR = "~D~";

    private final CustomerExtraFieldMapper extraFieldMapper;
    private final DataScopeHelper dataScope;
    private final SysUserMapper userMapper;
    private final NotificationService notificationService;

    public CustomerServiceImpl(CustomerExtraFieldMapper extraFieldMapper,
                               DataScopeHelper dataScope,
                               SysUserMapper userMapper,
                               NotificationService notificationService) {
        this.extraFieldMapper = extraFieldMapper;
        this.dataScope = dataScope;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Customer createCustomer(Customer customer) {
        Long id = IdWorker.getId();
        customer.setId(id);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("未登录，不能新建客户");
        }
        applyInitialPool(customer, currentUserId);
        prepareForSave(customer, null);
        if (!StringUtils.hasText(customer.getCurrency())) {
            customer.setCurrency("USD");
        }
        this.save(customer);
        return customer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCustomer(Customer customer) {
        if (customer.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        Customer existing = super.getById(customer.getId());
        if (existing == null) {
            throw new BusinessException("客户不存在");
        }
        ensureVisible(existing);
        customer.setCreateBy(existing.getCreateBy());
        // 客户池及担当只能走专用分配动作，普通编辑绝不能篡改归属。
        customer.setOwnerId(existing.getOwnerId());
        customer.setPoolType(existing.getPoolType());
        customer.setOwnerAssignedAt(existing.getOwnerAssignedAt());
        prepareForSave(customer, customer.getId());
        this.updateById(customer);
    }

    @Override
    public Customer getById(Serializable id) {
        Customer customer = super.getById(id);
        ensureVisible(customer);
        fillDisplayFields(customer);
        return customer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCustomer(Long id) {
        if (id == null) {
            return;
        }
        Customer customer = super.getById(id);
        ensureVisible(customer);
        if (customer == null) {
            throw new BusinessException("客户不存在或已删除");
        }
        extraFieldMapper.delete(new LambdaQueryWrapper<CustomerExtraField>()
                .eq(CustomerExtraField::getCustomerId, id));
        int affected = baseMapper.archiveById(
                id,
                archivedCustomerCode(customer.getCustCode(), id),
                SecurityUtils.getCurrentUserId());
        if (affected == 0) {
            throw new BusinessException("客户不存在或已删除");
        }
    }

    static String archivedCustomerCode(String custCode, Long id) {
        String suffix = DELETED_CODE_SEPARATOR + id;
        String code = StringUtils.hasText(custCode) ? custCode.trim() : "CUSTOMER";
        int prefixLength = Math.max(0, CUSTOMER_CODE_MAX_LENGTH - suffix.length());
        if (code.length() > prefixLength) {
            code = code.substring(0, prefixLength);
        }
        return code + suffix;
    }

    @Override
    public IPage<Customer> pageQuery(PageQuery query, String poolType) {
        IPage<Customer> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()),
                buildWrapper(query.getKeyword(), poolType));
        page.getRecords().forEach(this::fillDisplayFields);
        return page;
    }

    @Override
    public List<Customer> listByKeyword(String keyword, String poolType) {
        List<Customer> list = this.list(buildWrapper(keyword, poolType));
        list.forEach(this::fillDisplayFields);
        return list;
    }

    private LambdaQueryWrapper<Customer> buildWrapper(String keyword, String requestedPoolType) {
        Long ownerId = dataScope.restrictOwnerId();
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .orderByAsc(Customer::getCustCode)
                .orderByDesc(Customer::getCreateTime);
        if (ownerId != null) {
            // 业务员只可见本人已确认担当的个人发展客户池，公共池永不暴露。
            wrapper.eq(Customer::getPoolType, PERSONAL_POOL)
                    .eq(Customer::getOwnerId, ownerId);
        } else {
            String poolType = normalizePoolType(requestedPoolType);
            if (poolType != null) {
                wrapper.eq(Customer::getPoolType, poolType);
            }
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Customer::getCustCode, keyword)
                    .or().like(Customer::getCompanyName, keyword)
                    .or().like(Customer::getContactName, keyword)
                    .or().like(Customer::getCountry, keyword)
                    .or().like(Customer::getPhone, keyword)
                    .or().like(Customer::getWebsite, keyword)
                    .or().like(Customer::getCustNameCn, keyword)
                    .or().like(Customer::getCustNameEn, keyword));
        }
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignFromPublicPool(Long customerId, Long ownerId) {
        requirePoolManager();
        if (customerId == null) {
            throw new BusinessException("客户不存在");
        }
        Customer customer = super.getById(customerId);
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }
        if (ownerId == null) {
            throw new BusinessException("请选择跟单人");
        }
        if (!PUBLIC_POOL.equals(customer.getPoolType()) || customer.getOwnerId() != null) {
            throw new BusinessException("仅公共发展客户池中未分配担当的客户可分配跟单人");
        }
        boolean salesmanExists = userMapper.selectActiveUsersByRoleKey("salesman").stream()
                .anyMatch(user -> ownerId.equals(user.getId()));
        if (!salesmanExists) {
            throw new BusinessException("跟单人必须是启用状态的业务员");
        }
        customer.setOwnerId(ownerId);
        customer.setPoolType(PERSONAL_POOL);
        customer.setOwnerAssignedAt(LocalDateTime.now());
        this.updateById(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferToPublicPool(Long customerId, String reason) {
        requirePoolManager();
        if (customerId == null) {
            throw new BusinessException("客户不存在");
        }
        Customer customer = super.getById(customerId);
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }
        if (!PERSONAL_POOL.equals(customer.getPoolType()) || customer.getOwnerId() == null) {
            throw new BusinessException("仅已分配担当的个人发展客户可转入公共客户池");
        }

        Long previousOwnerId = customer.getOwnerId();
        int updated = baseMapper.moveToPublicPool(
                customerId, previousOwnerId, SecurityUtils.getCurrentUserId());
        if (updated != 1) {
            throw new BusinessException("客户状态已变化，请刷新后重试");
        }
        customer.setOwnerId(null);
        customer.setPoolType(PUBLIC_POOL);
        customer.setOwnerAssignedAt(null);
        notifyPoolTransfer(customer, previousOwnerId, reason, false);
    }

    @Override
    public List<CustomerAssigneeOption> listAssignableSalesmen() {
        requirePoolManager();
        return userMapper.selectActiveUsersByRoleKey("salesman").stream()
                .map(this::toAssigneeOption)
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList();
    }

    @Override
    @Scheduled(cron = "${erp.customer.pool-recycle-cron:0 15 2 * * ?}")
    @Transactional(rollbackFor = Exception.class)
    public void recycleInactivePersonalCustomers() {
        for (Customer customer : baseMapper.selectInactivePersonalCustomers()) {
            Long previousOwnerId = customer.getOwnerId();
            int updated = baseMapper.moveToPublicPool(customer.getId(), previousOwnerId, null);
            if (updated != 1) {
                continue;
            }
            notifyPoolTransfer(customer, previousOwnerId,
                    "连续 6 个月未成交且当前没有进行中的 PI，系统已自动回收", true);
        }
    }

    @Override
    public List<CustomerExtraField> listExtraFields(Long customerId) {
        ensureVisible(super.getById(customerId));
        return extraFieldMapper.selectList(new LambdaQueryWrapper<CustomerExtraField>()
                .eq(CustomerExtraField::getCustomerId, customerId)
                .orderByAsc(CustomerExtraField::getSort)
                .orderByAsc(CustomerExtraField::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveExtraFields(Long customerId, List<CustomerExtraField> fields) {
        if (customerId == null || this.getById(customerId) == null) {
            throw new BusinessException("客户不存在");
        }
        extraFieldMapper.delete(new LambdaQueryWrapper<CustomerExtraField>()
                .eq(CustomerExtraField::getCustomerId, customerId));
        if (fields == null || fields.isEmpty()) {
            return;
        }
        int sort = 1;
        for (CustomerExtraField field : fields) {
            if (field == null || !StringUtils.hasText(field.getFieldKey())) {
                continue;
            }
            CustomerExtraField normalized = new CustomerExtraField();
            normalized.setCustomerId(customerId);
            normalized.setFieldKey(field.getFieldKey().trim());
            normalized.setFieldValue(StringUtils.hasText(field.getFieldValue()) ? field.getFieldValue().trim() : "");
            normalized.setSort(sort++);
            extraFieldMapper.insert(normalized);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(MultipartFile file) throws IOException {
        ExcelImportValidator.validateFile(file);
        ImportResult result = new ImportResult();
        List<CustomerExcel> rows = new ArrayList<>();
        ExcelUtil.read(file.getInputStream(), CustomerExcel.class, 500, rows::addAll);
        ExcelImportValidator.validateRowCount(rows.size());
        result.setTotal(rows.size());

        Set<String> reservedCodes = baseMapper.selectAllCodes().stream()
                .map(this::normalizeCode)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        List<CustomerImportRow> toSave = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            CustomerExcel row = rows.get(i);
            int rowNo = i + 1;
            if (!hasCustomerData(row)) {
                result.addError(rowNo, "该行为空或未识别到客户字段，请使用最新导入模板");
                continue;
            }
            Customer customer = new Customer();
            BeanUtils.copyProperties(row, customer);
            Long id = IdWorker.getId();
            customer.setId(id);
            Long currentUserId = SecurityUtils.getCurrentUserId();
            if (currentUserId == null) {
                result.addError(rowNo, "未登录，不能导入客户");
                continue;
            }
            applyInitialPool(customer, currentUserId);
            String requestedCode = normalizeCode(customer.getCustCode());
            String adjustment = null;
            if (!StringUtils.hasText(requestedCode)) {
                customer.setCustCode(nextCustomerCode(customer.getCountry(), reservedCodes));
            } else if (reservedCodes.contains(requestedCode)) {
                String assignedCode = nextCustomerCode(customer.getCountry(), reservedCodes);
                customer.setCustCode(assignedCode);
                adjustment = "Excel 客户ID " + requestedCode + " 已存在，已自动分配 " + assignedCode;
            } else {
                customer.setCustCode(requestedCode);
            }
            try {
                prepareForSave(customer, null);
            } catch (BusinessException e) {
                result.addError(rowNo, e.getMessage());
                continue;
            }
            reservedCodes.add(customer.getCustCode());
            if (!StringUtils.hasText(customer.getCurrency())) {
                customer.setCurrency("USD");
            }
            toSave.add(new CustomerImportRow(customer, rowNo, requestedCode, adjustment));
        }
        for (CustomerImportRow row : toSave) {
            Customer customer = row.customer();
            String adjustment = row.adjustment();
            try {
                this.save(customer);
            } catch (DuplicateKeyException e) {
                // 极少数并发导入可能在预查后抢占相同业务编号；重新分配并重试一次。
                String previousCode = customer.getCustCode();
                String assignedCode = nextCustomerCode(customer.getCountry(), reservedCodes);
                customer.setCustCode(assignedCode);
                reservedCodes.add(assignedCode);
                this.save(customer);
                adjustment = "客户ID " + previousCode + " 在导入时被占用，已自动分配 " + assignedCode;
            }
            if (StringUtils.hasText(adjustment)) {
                result.addWarning(row.rowNo(), adjustment, row.requestedCode(), customer.getCompanyName());
            } else {
                result.addSuccess(row.rowNo(), customer.getCustCode(), customer.getCompanyName());
            }
        }
        return result;
    }

    private record CustomerImportRow(Customer customer, int rowNo, String requestedCode, String adjustment) {
    }

    private void prepareForSave(Customer customer, Long excludeId) {
        syncLegacyFields(customer);
        validateCustomerFields(customer);
        String code = normalizeCode(customer.getCustCode());
        if (!StringUtils.hasText(code)) {
            code = nextCustomerCode(customer.getCountry());
        }
        checkCodeUnique(code, excludeId);
        customer.setCustCode(code);
    }

    static void validatePhoneLength(String phone) {
        ExcelImportValidator.validateText("联系电话", phone, CUSTOMER_PHONE_MAX_LENGTH);
    }

    private void validateCustomerFields(Customer customer) {
        ExcelImportValidator.validateText("客户ID", customer.getCustCode(), CUSTOMER_CODE_MAX_LENGTH);
        ExcelImportValidator.validateText("联系人姓名", customer.getContactName(), 128);
        ExcelImportValidator.validateText("职位", customer.getContactPosition(), 128);
        ExcelImportValidator.validateText("国家", customer.getCountry(), 64);
        ExcelImportValidator.validateText("公司名称", customer.getCompanyName(), 255);
        ExcelImportValidator.validateText("地址", customer.getAddress(), 512);
        ExcelImportValidator.validateText("币种", customer.getCurrency(), 8);
        ExcelImportValidator.validateText("网站", customer.getWebsite(), 255);
        validatePhoneLength(customer.getPhone());
        ExcelImportValidator.validateText("电子邮箱", customer.getEmail(), 128);
        ExcelImportValidator.validateText("社媒联系", customer.getSocialContact(), 255);
        ExcelImportValidator.validateText("备注", customer.getRemark(), 512);
        ExcelImportValidator.validateText("联系人姓名", customer.getContact(), 128);
    }

    private boolean hasCustomerData(CustomerExcel row) {
        return row != null && (StringUtils.hasText(row.getCustCode())
                || StringUtils.hasText(row.getContactName())
                || StringUtils.hasText(row.getContactPosition())
                || StringUtils.hasText(row.getCountry())
                || StringUtils.hasText(row.getCompanyName())
                || StringUtils.hasText(row.getAddress())
                || StringUtils.hasText(row.getCurrency())
                || StringUtils.hasText(row.getWebsite())
                || StringUtils.hasText(row.getPhone())
                || StringUtils.hasText(row.getEmail())
                || StringUtils.hasText(row.getSocialContact())
                || StringUtils.hasText(row.getRemark()));
    }

    private void syncLegacyFields(Customer customer) {
        if (!StringUtils.hasText(customer.getCompanyName())) {
            customer.setCompanyName(firstText(customer.getCustNameCn(), customer.getCustNameEn()));
        }
        if (!StringUtils.hasText(customer.getCustNameCn())) {
            customer.setCustNameCn(customer.getCompanyName());
        }
        if (!StringUtils.hasText(customer.getContactName())) {
            customer.setContactName(customer.getContact());
        }
        if (!StringUtils.hasText(customer.getContact())) {
            customer.setContact(customer.getContactName());
        }
        if (!StringUtils.hasText(customer.getCurrency())) {
            customer.setCurrency("USD");
        }
    }

    private void fillDisplayFields(Customer customer) {
        if (customer == null) {
            return;
        }
        if (!StringUtils.hasText(customer.getCompanyName())) {
            customer.setCompanyName(firstText(customer.getCustNameCn(), customer.getCustNameEn()));
        }
        if (!StringUtils.hasText(customer.getContactName())) {
            customer.setContactName(customer.getContact());
        }
        if (customer.getOwnerId() != null) {
            SysUser owner = userMapper.selectById(customer.getOwnerId());
            if (owner != null) {
                customer.setOwnerName(firstText(owner.getRealName(), firstText(owner.getNickname(), owner.getUsername())));
            }
        }
    }

    private void ensureVisible(Customer customer) {
        Long ownerId = dataScope.restrictOwnerId();
        if (customer == null || ownerId == null) {
            return;
        }
        if (!PERSONAL_POOL.equals(customer.getPoolType()) || !ownerId.equals(customer.getOwnerId())) {
            throw new BusinessException("无权查看该客户资料");
        }
    }

    private void requirePoolManager() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null || !dataScope.viewAll()) {
            throw new BusinessException("仅销售主管或管理员可以管理客户池");
        }
        Set<String> roleKeys = new HashSet<>(userMapper.selectRoleKeysByUserId(currentUserId));
        if (!roleKeys.contains("sales_manager")
                && !roleKeys.contains("admin")
                && !roleKeys.contains("super_admin")) {
            throw new BusinessException("仅销售主管或管理员可以管理客户池");
        }
    }

    private void notifyPoolTransfer(Customer customer, Long previousOwnerId, String reason, boolean automatic) {
        if (previousOwnerId == null) {
            return;
        }
        String customerName = firstText(customer.getCompanyName(), customer.getCustCode());
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : "未填写";
        String operator = automatic ? "系统" : currentOperatorName();
        String content = "您担当的客户“" + customerName + "”（客户ID：" + customer.getCustCode()
                + "）已由" + operator + "转移至公共发展客户池。转移理由：" + normalizedReason + "。";
        notificationService.createForUser(previousOwnerId, "个人客户转入公共客户池通知",
                content, NotificationType.SYSTEM);
    }

    private String currentOperatorName() {
        Long userId = SecurityUtils.getCurrentUserId();
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            return "管理员";
        }
        return firstText(user.getRealName(), firstText(user.getNickname(), user.getUsername()));
    }

    private void applyInitialPool(Customer customer, Long currentUserId) {
        boolean salesman = userMapper.selectActiveUsersByRoleKey("salesman").stream()
                .anyMatch(user -> currentUserId.equals(user.getId()));
        if (salesman) {
            customer.setOwnerId(currentUserId);
            customer.setPoolType(PERSONAL_POOL);
            customer.setOwnerAssignedAt(LocalDateTime.now());
            return;
        }
        // 主管、管理员等非业务员录入的是待分配线索，不能生成无业务员可见的“个人池”数据。
        customer.setOwnerId(null);
        customer.setPoolType(PUBLIC_POOL);
        customer.setOwnerAssignedAt(null);
    }

    private CustomerAssigneeOption toAssigneeOption(SysUser user) {
        String name = firstText(user.getRealName(), firstText(user.getNickname(), user.getUsername()));
        return new CustomerAssigneeOption(user.getId(), name, user.getUsername());
    }

    private String normalizePoolType(String poolType) {
        if (!StringUtils.hasText(poolType) || "ALL".equalsIgnoreCase(poolType)) {
            return null;
        }
        if (PERSONAL_POOL.equalsIgnoreCase(poolType)) {
            return PERSONAL_POOL;
        }
        if (PUBLIC_POOL.equalsIgnoreCase(poolType)) {
            return PUBLIC_POOL;
        }
        throw new BusinessException("不支持的客户池类型");
    }

    private String nextCustomerCode(String country) {
        return nextCustomerCode(country, Set.of());
    }

    private String nextCustomerCode(String country, Set<String> reservedCodes) {
        String prefix = countryPrefix(country);
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$", Pattern.CASE_INSENSITIVE);
        Set<String> used = new HashSet<>();
        int max = 0;
        for (String raw : baseMapper.selectAllCodes()) {
            String code = normalizeCode(raw);
            if (!StringUtils.hasText(code)) {
                continue;
            }
            used.add(code.toUpperCase(Locale.ROOT));
            Matcher matcher = pattern.matcher(code);
            if (!matcher.matches()) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Ignore codes whose numeric suffix is beyond integer range.
            }
        }
        if (reservedCodes != null) {
            reservedCodes.stream()
                    .map(this::normalizeCode)
                    .filter(StringUtils::hasText)
                    .forEach(used::add);
        }
        int seq = max + 1;
        String candidate;
        do {
            candidate = prefix + String.format(Locale.ROOT, "%04d", seq++);
        } while (used.contains(candidate));
        return candidate;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getCustCode, code);
        if (excludeId != null) {
            wrapper.ne(Customer::getId, excludeId);
        }
        if (this.count(wrapper) > 0) {
            throw new BusinessException("客户ID已存在: " + code);
        }
    }

    private String normalizeCode(String code) {
        return StringUtils.hasText(code) ? code.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    private String countryPrefix(String country) {
        if (!StringUtils.hasText(country)) {
            return "ST";
        }
        String raw = country.trim();
        if (raw.matches("(?i)^[a-z]{2}$")) {
            return raw.toUpperCase(Locale.ROOT);
        }
        String normalized = raw.toUpperCase(Locale.ROOT);
        if (normalized.equals("USA") || normalized.contains("UNITED STATES") || normalized.contains("AMERICA")
                || normalized.contains("美国")) return "US";
        if (normalized.equals("UK") || normalized.contains("UNITED KINGDOM") || normalized.contains("英国")) return "GB";
        for (String iso : Locale.getISOCountries()) {
            Locale locale = new Locale("", iso);
            String english = locale.getDisplayCountry(Locale.ENGLISH).toUpperCase(Locale.ROOT);
            String chinese = locale.getDisplayCountry(Locale.SIMPLIFIED_CHINESE).toUpperCase(Locale.ROOT);
            if (matchesCountryName(normalized, english) || matchesCountryName(normalized, chinese)) {
                return iso;
            }
        }
        return "ST";
    }

    private boolean matchesCountryName(String input, String countryName) {
        return StringUtils.hasText(countryName)
                && (input.equals(countryName) || input.contains(countryName) || countryName.contains(input));
    }
}
