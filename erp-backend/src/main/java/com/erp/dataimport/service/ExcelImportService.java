package com.erp.dataimport.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.security.SecurityUtils;
import com.erp.dataimport.dto.SheetPreview;
import com.erp.dataimport.entity.ImportBatch;
import com.erp.dataimport.entity.ImportRow;
import com.erp.dataimport.mapper.ImportBatchMapper;
import com.erp.dataimport.mapper.ImportRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 Excel 导入 - 上传 xlsx -> 探查 sheet/表头 -> 选定后整表入库为结构化数据(JSON)。
 */
@Slf4j
@Service
public class ExcelImportService {

    private static final int MAX_COLS = 60;
    private static final int SAMPLE_ROWS = 8;
    private static final DataFormatter FORMATTER = new DataFormatter();

    private final ImportBatchMapper batchMapper;
    private final ImportRowMapper rowMapper;

    public ExcelImportService(ImportBatchMapper batchMapper, ImportRowMapper rowMapper) {
        this.batchMapper = batchMapper;
        this.rowMapper = rowMapper;
    }

    /** 探查: 返回每个 sheet 的行列数 + 前几行样例(供前端选 sheet 与表头行) */
    public List<SheetPreview> inspect(MultipartFile file) {
        validate(file);
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            List<SheetPreview> list = new ArrayList<>();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                SheetPreview sp = new SheetPreview();
                sp.setSheetName(sheet.getSheetName());
                int last = sheet.getLastRowNum();
                sp.setRowCount(last + 1);
                int maxCol = 0;
                List<List<String>> sample = new ArrayList<>();
                int limit = Math.min(last, SAMPLE_ROWS - 1);
                for (int r = 0; r <= limit; r++) {
                    List<String> vals = readRow(sheet.getRow(r));
                    maxCol = Math.max(maxCol, vals.size());
                    sample.add(vals);
                }
                sp.setColCount(maxCol);
                sp.setSample(sample);
                list.add(sp);
            }
            return list;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("解析 Excel 失败: " + e.getMessage());
        }
    }

    /** 入库: 指定 sheet 与表头行, 整表读取为 列名->值 的 JSON 行 */
    @Transactional(rollbackFor = Exception.class)
    public ImportBatch commit(MultipartFile file, String sheetName, int headerRowIndex) {
        validate(file);
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                throw new BusinessException("找不到 sheet: " + sheetName);
            }
            Row headerRow = sheet.getRow(headerRowIndex);
            if (headerRow == null) {
                throw new BusinessException("表头行(第 " + (headerRowIndex + 1) + " 行)为空");
            }
            List<String> headers = readRow(headerRow);
            if (headers.isEmpty()) {
                throw new BusinessException("表头为空");
            }
            // 补全空列名 + 重名列去重(同名列追加 _2/_3, 否则 JSON 以列名为 key 会相互覆盖丢数据)
            Map<String, Integer> seen = new java.util.HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i);
                if (!StringUtils.hasText(h)) {
                    h = "列" + (i + 1);
                }
                Integer prev = seen.get(h);
                int n = (prev == null) ? 1 : prev + 1;
                seen.put(h, n);
                if (n > 1) {
                    h = h + "_" + n;
                }
                headers.set(i, h);
            }

            ImportBatch batch = new ImportBatch();
            batch.setFileName(file.getOriginalFilename());
            batch.setSheetName(sheetName);
            batch.setColumnsJson(JSONUtil.toJsonStr(headers));
            batch.setImporterName(currentUserName());
            batchMapper.insert(batch);

            int last = sheet.getLastRowNum();
            int count = 0;
            for (int r = headerRowIndex + 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> data = new LinkedHashMap<>();
                boolean empty = true;
                for (int c = 0; c < headers.size(); c++) {
                    String v = cellToString(row.getCell(c));
                    if (StringUtils.hasText(v)) {
                        empty = false;
                    }
                    data.put(headers.get(c), v);
                }
                if (empty) {
                    continue;
                }
                ImportRow ir = new ImportRow();
                ir.setBatchId(batch.getId());
                ir.setRowIndex(r + 1);
                ir.setDataJson(JSONUtil.toJsonStr(data));
                rowMapper.insert(ir);
                count++;
            }
            batch.setRowCount(count);
            batchMapper.updateById(batch);
            log.info("[IMPORT] {} / {} -> {} 行入库", file.getOriginalFilename(), sheetName, count);
            return batch;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("导入失败: " + e.getMessage());
        }
    }

    public IPage<ImportBatch> pageBatches(PageQuery query) {
        LambdaQueryWrapper<ImportBatch> w = new LambdaQueryWrapper<ImportBatch>()
                .orderByDesc(ImportBatch::getCreateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            w.and(x -> x.like(ImportBatch::getFileName, kw).or().like(ImportBatch::getSheetName, kw));
        }
        return batchMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), w);
    }

    public IPage<ImportRow> pageRows(Long batchId, PageQuery query) {
        return rowMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<ImportRow>lambdaQuery().eq(ImportRow::getBatchId, batchId).orderByAsc(ImportRow::getRowIndex));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long batchId) {
        batchMapper.deleteById(batchId);
        rowMapper.delete(Wrappers.<ImportRow>lambdaQuery().eq(ImportRow::getBatchId, batchId));
    }

    // ---------------- 私有 ----------------

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        String name = file.getOriginalFilename();
        if (name == null || !(name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".xls"))) {
            throw new BusinessException("仅支持 .xlsx/.xls 文件");
        }
    }

    private List<String> readRow(Row row) {
        List<String> vals = new ArrayList<>();
        if (row == null) {
            return vals;
        }
        int lastCol = Math.min(row.getLastCellNum(), MAX_COLS);
        for (int c = 0; c < lastCol; c++) {
            vals.add(cellToString(row.getCell(c)));
        }
        // 去除尾部空列
        while (!vals.isEmpty() && !StringUtils.hasText(vals.get(vals.size() - 1))) {
            vals.remove(vals.size() - 1);
        }
        return vals;
    }

    private String cellToString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield FORMATTER.formatCellValue(cell);
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    yield String.valueOf((long) d);
                }
                yield BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
            }
            case FORMULA -> {
                try {
                    yield FORMATTER.formatCellValue(cell);
                } catch (Exception e) {
                    yield "";
                }
            }
            default -> "";
        };
    }

    private String currentUserName() {
        var u = SecurityUtils.getLoginUser();
        if (u == null) {
            return "系统";
        }
        return StringUtils.hasText(u.getUser().getRealName()) ? u.getUser().getRealName() : u.getUsername();
    }
}
