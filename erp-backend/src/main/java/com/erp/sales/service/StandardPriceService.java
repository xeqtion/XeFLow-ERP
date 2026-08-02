package com.erp.sales.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.excel.ImportResult;
import com.erp.common.page.PageQuery;
import com.erp.sales.entity.StandardPrice;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface StandardPriceService extends IService<StandardPrice> {

    IPage<StandardPrice> pageQuery(PageQuery query);

    List<StandardPrice> listByKeyword(String keyword);

    Long saveQuote(StandardPrice quote);

    void updateQuote(StandardPrice quote);

    /** 取适用标准价 (客户专属优先, 否则通用) */
    StandardPrice findApplicable(Long productId, Long customerId);

    ImportResult importExcel(MultipartFile file) throws IOException;
}
