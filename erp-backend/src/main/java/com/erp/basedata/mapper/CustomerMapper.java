package com.erp.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.basedata.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {

    @Select("SELECT cust_code FROM customer WHERE deleted = 0 AND cust_code IS NOT NULL")
    List<String> selectAllCodes();

    @Select("SELECT id FROM customer WHERE deleted = 0 AND cust_code = #{custCode} LIMIT 1")
    Long selectIdByCode(@Param("custCode") String custCode);

    @Update("""
            UPDATE customer
            SET cust_code = #{archivedCustCode},
                update_by = #{updateBy},
                update_time = NOW(),
                deleted = 1
            WHERE id = #{id} AND deleted = 0
            """)
    int archiveById(@Param("id") Long id,
                    @Param("archivedCustCode") String archivedCustCode,
                    @Param("updateBy") Long updateBy);

    @Update("""
            UPDATE customer
            SET owner_id = NULL,
                pool_type = 'PUBLIC',
                owner_assigned_at = NULL,
                update_by = COALESCE(#{updateBy}, update_by),
                update_time = NOW()
            WHERE id = #{id}
              AND deleted = 0
              AND pool_type = 'PERSONAL'
              AND owner_id = #{previousOwnerId}
            """)
    int moveToPublicPool(@Param("id") Long id,
                         @Param("previousOwnerId") Long previousOwnerId,
                         @Param("updateBy") Long updateBy);

    /**
     * 个人池客户在六个月内没有任何已确认成交，且没有进行中的 PI 时，才允许回收至公共池。
     */
    @Select("""
            SELECT c.*
            FROM customer c
            WHERE c.deleted = 0
              AND c.pool_type = 'PERSONAL'
              AND c.owner_id IS NOT NULL
              AND COALESCE(c.owner_assigned_at, c.create_time) < DATE_SUB(NOW(), INTERVAL 6 MONTH)
              AND NOT EXISTS (
                SELECT 1 FROM sales_pi active_pi
                WHERE active_pi.customer_id = c.id
                  AND active_pi.deleted = 0
                  AND active_pi.status IN ('CONFIRMED', 'ORDERED', 'SHIPPED')
              )
              AND NOT EXISTS (
                SELECT 1 FROM sales_pi recent_pi
                WHERE recent_pi.customer_id = c.id
                  AND recent_pi.deleted = 0
                  AND recent_pi.status IN ('CONFIRMED', 'ORDERED', 'SHIPPED', 'COMPLETED')
                  AND COALESCE(recent_pi.sign_date, DATE(recent_pi.create_time)) >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
              )
            """)
    List<Customer> selectInactivePersonalCustomers();
}
