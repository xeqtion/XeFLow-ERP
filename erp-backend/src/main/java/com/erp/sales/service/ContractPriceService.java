package com.erp.sales.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.sales.dto.ContractPriceQuery;
import com.erp.sales.entity.ContractPrice;

public interface ContractPriceService extends IService<ContractPrice> {

    /** 审核状态常量 */
    String PASS = "PASS";
    String PENDING = "PENDING";
    String REJECTED = "REJECTED";

    IPage<ContractPrice> pageQuery(ContractPriceQuery query);

    /** 详情: 审核员可看全部, 普通用户仅可看自己提交的报价。 */
    ContractPrice getVisible(Long id);

    /** 新建销售报价: 自动按产品报价区间判定审核状态。 */
    Long createWithCompare(ContractPrice cp);

    /** 修改销售报价: 重新比对并重置审核状态。 */
    void updateWithCompare(ContractPrice cp);

    /** 撤销报价, 即删除报价。 */
    void cancel(Long id);

    /** 审批通过。 */
    void approve(Long id);

    /** 审批驳回。 */
    void reject(Long id, String reason);

    /** 重新进入待审核。 */
    void requestReview(Long id);
}
