package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Bom;
import com.erp.basedata.dto.BomExcel;
import com.erp.basedata.mapper.BomMapper;
import com.erp.common.excel.ExcelImportValidator;
import com.erp.common.excel.ExcelUtil;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BomServiceImpl extends ServiceImpl<BomMapper, Bom> implements BomService {

    private static final Logger log = LoggerFactory.getLogger(BomServiceImpl.class);
    private static final Pattern MATERIAL_CODE_PATTERN = Pattern.compile("^MT(\\d{4,})$");

    @Override
    public IPage<Bom> pageQuery(PageQuery query, String productModel, Boolean universalOnly) {
        LambdaQueryWrapper<Bom> wrapper = new LambdaQueryWrapper<Bom>().orderByAsc(Bom::getBomCode);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Bom::getBomCode, keyword)
                    .or().like(Bom::getNameCn, keyword)
                    .or().like(Bom::getSupportModels, keyword)
                    .or().like(Bom::getSpec, keyword)
                    .or().like(Bom::getSupplier, keyword)
                    .or().like(Bom::getSupplierContact, keyword)
                    .or().like(Bom::getSupplierPhone, keyword));
        }
        if (StringUtils.hasText(productModel)) {
            String model = productModel.trim();
            wrapper.and(w -> w.eq(Bom::getUniversal, 1)
                    .or().like(Bom::getSupportModels, "所有")
                    .or().like(Bom::getSupportModels, model));
        }
        if (Boolean.TRUE.equals(universalOnly)) {
            wrapper.and(w -> w.eq(Bom::getUniversal, 1)
                    .or().like(Bom::getSupportModels, "所有"));
        }
        return this.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    @Override
    public Bom getDetail(Long id) {
        Bom bom = this.getById(id);
        if (bom == null) {
            throw new BusinessException("产品配件资料不存在");
        }
        return bom;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveBom(Bom bom) {
        bom.setId(null);
        prepareForSave(bom, null);
        try {
            this.save(bom);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("物料编码已存在，请刷新后重试");
        }
        return bom.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBom(Bom bom) {
        if (bom.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        if (this.getById(bom.getId()) == null) {
            throw new BusinessException("产品配件资料不存在");
        }
        prepareForSave(bom, bom.getId());
        try {
            this.updateById(bom);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("物料编码已存在，请使用其他编号");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeBom(Long id) {
        Bom existing = this.getById(id);
        if (existing == null) {
            throw new BusinessException("产品配件资料不存在");
        }
        try {
            baseMapper.archiveDeletedCodeConflicts(existing.getBomCode());
            if (!this.removeById(id)) {
                throw new BusinessException("产品配件资料不存在");
            }
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("删除失败，配件数据存在编号冲突，请刷新后重试");
        }
    }

    @Override
    public List<Bom> listByKeyword(String keyword) {
        LambdaQueryWrapper<Bom> wrapper = new LambdaQueryWrapper<Bom>()
                .orderByAsc(Bom::getBomCode);
        applyKeyword(wrapper, keyword);
        return this.list(wrapper);
    }

    @Override
    public ImportResult importExcel(MultipartFile file) throws IOException {
        ExcelImportValidator.validateFile(file);
        List<BomExcel> rows = new ArrayList<>();
        ExcelUtil.read(file.getInputStream(), BomExcel.class, 500, rows::addAll);
        ExcelImportValidator.validateRowCount(rows.size());

        ImportResult result = new ImportResult();
        result.setTotal(rows.size());
        if (rows.isEmpty()) {
            return result;
        }

        Map<String, Bom> activeByCode = new HashMap<>();
        for (Bom bom : this.list()) {
            if (StringUtils.hasText(bom.getBomCode())) {
                activeByCode.put(normalizeCode(bom.getBomCode()), bom);
            }
        }
        Set<String> historicalCodes = new HashSet<>();
        for (String code : baseMapper.selectAllCodes()) {
            if (StringUtils.hasText(code)) {
                historicalCodes.add(normalizeCode(code));
            }
        }
        Set<String> explicitCodesInFile = new HashSet<>();

        for (int index = 0; index < rows.size(); index++) {
            BomExcel row = rows.get(index);
            int excelRow = index + 2;
            if (isEmptyRow(row)) {
                result.addWarning(excelRow, "已跳过空行");
                continue;
            }

            String requestedCode = normalizeCode(row.getBomCode());
            try {
                if (StringUtils.hasText(requestedCode) && !explicitCodesInFile.add(requestedCode)) {
                    throw new BusinessException("文件内物料编码重复: " + requestedCode);
                }

                Bom existing = StringUtils.hasText(requestedCode) ? activeByCode.get(requestedCode) : null;
                if (existing == null
                        && StringUtils.hasText(requestedCode)
                        && historicalCodes.contains(requestedCode)) {
                    throw new BusinessException("物料编码已被使用并删除，请留空让系统自动生成新编号");
                }

                Bom imported = fromExcel(row);
                boolean generated = !StringUtils.hasText(requestedCode);
                imported.setBomCode(generated
                        ? nextMaterialCode(historicalCodes)
                        : requestedCode);
                normalizeForSave(imported);
                validateImportedBom(imported);

                if (existing != null) {
                    imported.setId(existing.getId());
                    if (!this.updateById(imported)) {
                        throw new BusinessException("更新失败，记录可能已被其他用户删除");
                    }
                    activeByCode.put(imported.getBomCode(), imported);
                    result.addWarning(excelRow, "已更新现有配件资料",
                            imported.getBomCode(), imported.getNameCn());
                } else {
                    insertImportedBom(imported, generated, historicalCodes);
                    historicalCodes.add(imported.getBomCode());
                    activeByCode.put(imported.getBomCode(), imported);
                    result.addSuccess(excelRow, imported.getBomCode(), imported.getNameCn());
                }
            } catch (BusinessException e) {
                result.addError(excelRow, e.getMessage(), requestedCode, row.getNameCn());
            } catch (DataIntegrityViolationException e) {
                result.addError(excelRow, "数据与现有记录冲突，请检查物料编码或字段长度",
                        requestedCode, row.getNameCn());
            } catch (RuntimeException e) {
                log.warn("配件导入第 {} 行失败: {}", excelRow, e.getMessage());
                result.addError(excelRow, "该行无法导入，请检查数据格式后重试",
                        requestedCode, row.getNameCn());
            }
        }
        return result;
    }

    private void prepareForSave(Bom bom, Long excludeId) {
        if (!StringUtils.hasText(bom.getBomCode())) {
            bom.setBomCode(nextMaterialCode());
        } else {
            bom.setBomCode(normalizeCode(bom.getBomCode()));
        }
        checkCodeUnique(bom.getBomCode(), excludeId);
        normalizeForSave(bom);
    }

    private void normalizeForSave(Bom bom) {
        if (StringUtils.hasText(bom.getNameCn())) {
            bom.setNameCn(bom.getNameCn().trim());
        }
        if (StringUtils.hasText(bom.getSupportModels())) {
            bom.setSupportModels(normalizeSupportModels(bom.getSupportModels()));
        }
        if (StringUtils.hasText(bom.getCurrency())) {
            bom.setCurrency(bom.getCurrency().trim().toUpperCase());
        } else {
            bom.setCurrency("CNY");
        }
        if (Boolean.TRUE.equals(isUniversal(bom))) {
            bom.setUniversal(1);
            bom.setSupportModels("所有");
        } else {
            bom.setUniversal(0);
        }
    }

    private Boolean isUniversal(Bom bom) {
        return Integer.valueOf(1).equals(bom.getUniversal())
                || (StringUtils.hasText(bom.getSupportModels()) && bom.getSupportModels().contains("所有"));
    }

    private String normalizeSupportModels(String models) {
        return models.replace('，', ',')
                .replace('、', ',')
                .replace("\r\n", ",")
                .replace('\n', ',')
                .replace('\r', ',')
                .replaceAll("\\s*,\\s*", ",")
                .replaceAll(",+", ",")
                .replaceAll("^,|,$", "")
                .trim();
    }

    private String nextMaterialCode() {
        return nextMaterialCode(new HashSet<>(baseMapper.selectAllCodes()));
    }

    private String nextMaterialCode(Set<String> usedCodes) {
        int max = 0;
        for (String code : usedCodes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            Matcher matcher = MATERIAL_CODE_PATTERN.matcher(code.trim().toUpperCase());
            if (matcher.matches()) {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        String candidate;
        do {
            candidate = String.format("MT%04d", ++max);
        } while (usedCodes.contains(candidate));
        return candidate;
    }

    private void checkCodeUnique(String bomCode, Long excludeId) {
        if (!StringUtils.hasText(bomCode)) {
            throw new BusinessException("物料编码不能为空");
        }
        LambdaQueryWrapper<Bom> wrapper = new LambdaQueryWrapper<Bom>().eq(Bom::getBomCode, bomCode);
        if (excludeId != null) {
            wrapper.ne(Bom::getId, excludeId);
        }
        if (this.count(wrapper) > 0) {
            throw new BusinessException("物料编码已存在: " + bomCode);
        }
    }

    private void applyKeyword(LambdaQueryWrapper<Bom> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String value = keyword.trim();
        wrapper.and(w -> w.like(Bom::getBomCode, value)
                .or().like(Bom::getNameCn, value)
                .or().like(Bom::getSupportModels, value)
                .or().like(Bom::getSpec, value)
                .or().like(Bom::getSupplier, value)
                .or().like(Bom::getSupplierContact, value)
                .or().like(Bom::getSupplierPhone, value));
    }

    private Bom fromExcel(BomExcel row) {
        Bom bom = new Bom();
        bom.setNameCn(trimToNull(row.getNameCn()));
        bom.setUniversal(parseUniversal(row.getUniversal(), row.getSupportModels()));
        bom.setSupportModels(trimToNull(row.getSupportModels()));
        bom.setSpec(trimToNull(row.getSpec()));
        bom.setQty(parseDecimal("用量", row.getQty()));
        bom.setSupplier(trimToNull(row.getSupplier()));
        bom.setSupplierPhone(trimToNull(row.getSupplierPhone()));
        bom.setSupplierContact(trimToNull(row.getSupplierContact()));
        bom.setUnitPrice(parseDecimal("单价", row.getUnitPrice()));
        bom.setCurrency(trimToNull(row.getCurrency()));
        bom.setRemark(trimToNull(row.getRemark()));
        return bom;
    }

    private void validateImportedBom(Bom bom) {
        if (!StringUtils.hasText(bom.getNameCn())) {
            throw new BusinessException("名称不能为空");
        }
        if (!MATERIAL_CODE_PATTERN.matcher(bom.getBomCode()).matches()) {
            throw new BusinessException("物料编码格式应为 MT0001");
        }
        ExcelImportValidator.validateText("物料编码", bom.getBomCode(), 64);
        ExcelImportValidator.validateText("名称", bom.getNameCn(), 255);
        ExcelImportValidator.validateText("支持产品型号", bom.getSupportModels(), 1024);
        ExcelImportValidator.validateText("规格", bom.getSpec(), 255);
        ExcelImportValidator.validateText("供应商", bom.getSupplier(), 255);
        ExcelImportValidator.validateText("联系电话", bom.getSupplierPhone(), 64);
        ExcelImportValidator.validateText("联系人", bom.getSupplierContact(), 128);
        ExcelImportValidator.validateText("币种", bom.getCurrency(), 8);
        ExcelImportValidator.validateText("备注", bom.getRemark(), 512);
        validateNonNegative("用量", bom.getQty());
        validateNonNegative("单价", bom.getUnitPrice());
        ExcelImportValidator.validateDecimal("用量", bom.getQty(), 14, 4);
        ExcelImportValidator.validateDecimal("单价", bom.getUnitPrice(), 14, 4);
    }

    private void insertImportedBom(Bom bom, boolean generated, Set<String> usedCodes) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (!this.save(bom)) {
                    throw new BusinessException("新增配件资料失败");
                }
                return;
            } catch (DataIntegrityViolationException e) {
                if (!generated || attempt == 2) {
                    throw e;
                }
                usedCodes.add(bom.getBomCode());
                usedCodes.addAll(baseMapper.selectAllCodes());
                bom.setId(null);
                bom.setBomCode(nextMaterialCode(usedCodes));
            }
        }
    }

    private static Integer parseUniversal(String value, String supportModels) {
        if (!StringUtils.hasText(value)) {
            return StringUtils.hasText(supportModels) && supportModels.contains("所有") ? 1 : 0;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (Set.of("是", "通用", "1", "true", "yes", "y").contains(normalized)) {
            return 1;
        }
        if (Set.of("否", "非通用", "0", "false", "no", "n").contains(normalized)) {
            return 0;
        }
        throw new BusinessException("是否通用仅支持：是/否");
    }

    private static BigDecimal parseDecimal(String fieldName, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new BusinessException(fieldName + "必须是数字");
        }
    }

    private static void validateNonNegative(String fieldName, BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw new BusinessException(fieldName + "不能小于 0");
        }
    }

    private static boolean isEmptyRow(BomExcel row) {
        return !StringUtils.hasText(row.getBomCode())
                && !StringUtils.hasText(row.getNameCn())
                && !StringUtils.hasText(row.getSupportModels())
                && !StringUtils.hasText(row.getSpec())
                && !StringUtils.hasText(row.getSupplier())
                && !StringUtils.hasText(row.getUnitPrice())
                && !StringUtils.hasText(row.getQty())
                && !StringUtils.hasText(row.getRemark());
    }

    private static String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
