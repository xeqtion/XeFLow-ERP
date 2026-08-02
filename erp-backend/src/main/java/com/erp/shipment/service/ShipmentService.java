package com.erp.shipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.excel.ImportResult;
import com.erp.common.page.PageQuery;
import com.erp.shipment.entity.Shipment;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ShipmentService extends IService<Shipment> {

    IPage<Shipment> pageQuery(PageQuery query);

    List<Shipment> listByKeyword(String keyword);

    ImportResult importExcel(MultipartFile file) throws IOException;
}
