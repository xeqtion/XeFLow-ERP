package com.erp.finance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.excel.ImportResult;
import com.erp.finance.dto.FinanceEntryQuery;
import com.erp.finance.dto.FinanceSummary;
import com.erp.finance.entity.FinanceEntry;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface FinanceEntryService extends IService<FinanceEntry> {

    IPage<FinanceEntry> pageQuery(FinanceEntryQuery query);

    List<FinanceEntry> listForExport(FinanceEntryQuery query);

    /** 新建/修改前补全方向(按类型默认) */
    void fillDirection(FinanceEntry entry);

    /** 按收付方向和流水原币分别汇总。 */
    List<FinanceSummary> summary(FinanceEntryQuery query);

    /** 经营报表专用：按类型汇总并依入账日汇率折算为 CNY。 */
    List<FinanceSummary> summaryInBase(FinanceEntryQuery query);

    ImportResult importExcel(MultipartFile file) throws IOException;
}
