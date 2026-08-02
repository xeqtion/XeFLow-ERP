package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Bom;
import com.erp.common.excel.ImportResult;
import com.erp.common.page.PageQuery;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface BomService extends IService<Bom> {

    IPage<Bom> pageQuery(PageQuery query, String productModel, Boolean universalOnly);

    Bom getDetail(Long id);

    Long saveBom(Bom bom);

    void updateBom(Bom bom);

    void removeBom(Long id);

    List<Bom> listByKeyword(String keyword);

    ImportResult importExcel(MultipartFile file) throws IOException;
}
