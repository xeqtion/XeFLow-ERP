package com.erp.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.sales.entity.StandardPrice;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface StandardPriceMapper extends BaseMapper<StandardPrice> {

    /**
     * 取适用的产品报价: 优先客户专属价, 否则通用价(customer_id 为空), 取最新生效。
     */
    @Select("""
            SELECT * FROM standard_price
            WHERE product_id = #{productId} AND deleted = 0
              AND (customer_id = #{customerId} OR customer_id IS NULL)
            ORDER BY (customer_id IS NULL) ASC, effective_date DESC, id DESC
            LIMIT 1
            """)
    StandardPrice findApplicable(@Param("productId") Long productId,
                                 @Param("customerId") Long customerId);

    /**
     * 按产品ID+币种查找已有报价（幂等性检查用）。
     */
    @Select("""
            SELECT * FROM standard_price
            WHERE product_id = #{productId}
              AND currency = #{currency}
              AND customer_id IS NULL
              AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    StandardPrice findByProductAndCurrency(@Param("productId") Long productId,
                                            @Param("currency") String currency);

    /**
     * 批量插入（跳过 MyBatis-Plus 自动填充，需手动设审计字段）。
     */
    @Insert({"<script>",
            "INSERT INTO standard_price (id, product_id, prod_code, product_name, company_model,",
            "  customer_id, standard_unit_price, discount_unit_price, floor_price, price, currency, remark,",
            "  create_by, create_time, update_by, update_time) VALUES ",
            "<foreach collection='list' item='item' separator=','>",
            "(#{item.id}, #{item.productId}, #{item.prodCode}, #{item.productName}, #{item.companyModel},",
            " #{item.customerId}, #{item.standardUnitPrice}, #{item.discountUnitPrice}, #{item.floorPrice}, #{item.price}, #{item.currency}, #{item.remark},",
            " #{item.createBy}, #{item.createTime}, #{item.updateBy}, #{item.updateTime})",
            "</foreach></script>"})
    int insertBatch(@Param("list") List<StandardPrice> list);

    /**
     * 批量更新（按 id）。
     */
    @Update({"<script>",
            "<foreach collection='list' item='item' separator=';'>",
            "UPDATE standard_price SET",
            "  product_id = #{item.productId},",
            "  prod_code = #{item.prodCode},",
            "  product_name = #{item.productName},",
            "  company_model = #{item.companyModel},",
            "  standard_unit_price = #{item.standardUnitPrice},",
            "  discount_unit_price = #{item.discountUnitPrice},",
            "  floor_price = #{item.floorPrice},",
            "  price = #{item.price},",
            "  currency = #{item.currency},",
            "  remark = #{item.remark},",
            "  update_by = #{item.updateBy},",
            "  update_time = #{item.updateTime}",
            "WHERE id = #{item.id} AND deleted = 0",
            "</foreach></script>"})
    int updateBatch(@Param("list") List<StandardPrice> list);
}
