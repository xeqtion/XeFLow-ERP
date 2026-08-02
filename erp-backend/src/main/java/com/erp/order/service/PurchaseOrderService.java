package com.erp.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.page.PageQuery;
import com.erp.order.dto.PurchaseOrderDTO;
import com.erp.order.dto.PoReceiptDTO;
import com.erp.order.entity.PurchaseOrder;
import com.erp.order.entity.PurchaseOrderItem;
import com.erp.order.dto.PurchaserOption;

import java.util.List;

public interface PurchaseOrderService extends IService<PurchaseOrder> {

    IPage<PurchaseOrder> pageQuery(PageQuery query, Long purchaserId);

    List<PurchaserOption> purchaserOptions();

    PurchaseOrderDTO getDetail(Long id);

    Long savePo(PurchaseOrderDTO dto);

    void updatePo(PurchaseOrderDTO dto);

    void removePo(Long id);

    List<PurchaseOrderItem> listItems(Long poId);

    /** Confirm a draft PO before it is included in accounts payable. */
    void confirm(Long id);

    /** Receive specified quantities for PO lines and update its progress status. */
    void receive(Long id, PoReceiptDTO receipt);
}
