package com.erp.report.mapper;

import com.erp.report.dto.ReportAmountSource;
import com.erp.report.dto.SalesPerformanceDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表聚合查询 - 按周期(DATE_FORMAT 模板)对金额汇总。
 */
@Mapper
public interface ReportMapper {

    @Select("""
            SELECT order_date AS businessDate,
                   currency,
                   total_amount AS amount
            FROM purchase_order
            WHERE deleted = 0 AND order_date IS NOT NULL
              AND status IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'RECEIVED')
              AND (#{start} IS NULL OR order_date >= #{start})
              AND (#{end} IS NULL OR order_date <= #{end})
            ORDER BY order_date, id
            """)
    List<ReportAmountSource> purchaseSources(@Param("start") LocalDate start,
                                             @Param("end") LocalDate end);

    @Select("""
            SELECT sign_date AS businessDate,
                   currency,
                   total_amount AS amount
            FROM sales_pi
            WHERE deleted = 0 AND sign_date IS NOT NULL
              AND status IN ('CONFIRMED', 'ORDERED', 'SHIPPED', 'COMPLETED', 'CLOSED')
              AND (#{start} IS NULL OR sign_date >= #{start})
              AND (#{end} IS NULL OR sign_date <= #{end})
              AND (#{ownerId} IS NULL OR owner_id = #{ownerId})
            ORDER BY sign_date, id
            """)
    List<ReportAmountSource> salesSources(@Param("start") LocalDate start,
                                         @Param("end") LocalDate end,
                                         @Param("ownerId") Long ownerId);

    /** 按业务员(担当)汇总销售额(基于 PI 的 owner_id) */
    @Select("""
            SELECT p.owner_id AS ownerId,
                   COALESCE(u.real_name, u.username, '未分配') AS salesman,
                   p.sign_date AS businessDate,
                   p.currency,
                   p.total_amount AS amount
            FROM sales_pi p
            LEFT JOIN sys_user u ON u.id = p.owner_id
            WHERE p.deleted = 0 AND p.sign_date IS NOT NULL
              AND p.status IN ('CONFIRMED', 'ORDERED', 'SHIPPED', 'COMPLETED', 'CLOSED')
              AND (#{start} IS NULL OR p.sign_date >= #{start})
              AND (#{end} IS NULL OR p.sign_date <= #{end})
            ORDER BY p.owner_id, p.sign_date, p.id
            """)
    List<ReportAmountSource> salesmanSources(@Param("start") LocalDate start,
                                             @Param("end") LocalDate end);

    @Select("""
            SELECT p.id AS piId,
                   p.pi_no AS piNo,
                   p.owner_id AS ownerId,
                   COALESCE(u.real_name, u.username, '未分配') AS salesman,
                   c.cust_code AS customerCode,
                   COALESCE(c.company_name, c.cust_name_en, c.cust_name_cn, c.cust_code, '-') AS customerName,
                   p.sign_date AS signDate,
                   p.status,
                   p.currency,
                   p.total_amount AS originalAmount
            FROM sales_pi p
            LEFT JOIN sys_user u ON u.id = p.owner_id
            LEFT JOIN customer c ON c.id = p.customer_id AND c.deleted = 0
            WHERE p.deleted = 0 AND p.sign_date IS NOT NULL
              AND p.status IN ('CONFIRMED', 'ORDERED', 'SHIPPED', 'COMPLETED', 'CLOSED')
              AND (#{ownerId} IS NULL OR p.owner_id = #{ownerId})
              AND (#{start} IS NULL OR p.sign_date >= #{start})
              AND (#{end} IS NULL OR p.sign_date <= #{end})
            ORDER BY p.sign_date DESC, p.id DESC
            """)
    List<SalesPerformanceDetail> salesmanDetails(@Param("ownerId") Long ownerId,
                                                  @Param("start") LocalDate start,
                                                  @Param("end") LocalDate end);
}
