package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Product;
import com.erp.common.excel.ImportResult;
import com.erp.common.page.PageQuery;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ProductService extends IService<Product> {

    /** Create product; blank code is generated automatically. */
    Product createProduct(Product product);

    /** Update product; blank code is generated automatically. */
    void updateProduct(Product product);

    /** Update product by original id; product id may be changed if the new id is unique. */
    void updateProduct(Long originalId, Product product);

    IPage<Product> pageQuery(PageQuery query);

    List<Product> listByKeyword(String keyword);

    ImportResult importExcel(MultipartFile file) throws IOException;
}
