package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.entity.CustomerExtraField;
import com.erp.basedata.dto.CustomerAssigneeOption;
import com.erp.common.excel.ImportResult;
import com.erp.common.page.PageQuery;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface CustomerService extends IService<Customer> {

    /** Create customer; blank code is generated automatically. */
    Customer createCustomer(Customer customer);

    /** Update customer; blank code is generated automatically. */
    void updateCustomer(Customer customer);

    /** Delete customer and its detail extra fields. */
    void deleteCustomer(Long id);

    /** 分页查询 (按编码/中英文名模糊) */
    IPage<Customer> pageQuery(PageQuery query, String poolType);

    /** 按条件查询列表 (用于导出) */
    List<Customer> listByKeyword(String keyword, String poolType);

    /** 将公共发展客户池客户分配给一个业务员跟单。 */
    void assignFromPublicPool(Long customerId, Long ownerId);

    /** 将已分配担当的个人客户转回公共发展客户池，并通知原担当。 */
    void transferToPublicPool(Long customerId, String reason);

    /** 可作为客户担当的启用业务员。 */
    List<CustomerAssigneeOption> listAssignableSalesmen();

    /** 每日回收连续六个月无成交且无进行中 PI 的个人发展客户。 */
    void recycleInactivePersonalCustomers();

    /** 客户详情自定义键值 */
    List<CustomerExtraField> listExtraFields(Long customerId);

    /** 覆盖保存客户详情自定义键值 */
    void saveExtraFields(Long customerId, List<CustomerExtraField> fields);

    /** Excel 导入, 返回逐行校验结果 */
    ImportResult importExcel(MultipartFile file) throws IOException;
}
