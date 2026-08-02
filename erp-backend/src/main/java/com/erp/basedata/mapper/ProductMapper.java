package com.erp.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.basedata.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Select("""
            SELECT GREATEST(COALESCE(MAX(id), 99999) + 1, 100000)
              FROM product
             WHERE id >= 100000
               AND id < 1000000000
            """)
    Long selectNextProductId();

    @Select("SELECT * FROM product WHERE factory_model = #{factoryModel} AND deleted = 0 LIMIT 1")
    Product selectByFactoryModel(String factoryModel);

    @Select({"<script>",
            "SELECT * FROM product WHERE factory_model IN ",
            "<foreach collection='list' item='item' open='(' separator=',' close=')'>",
            "#{item}</foreach> AND deleted = 0",
            "</script>"})
    List<Product> selectByFactoryModelList(@Param("list") List<String> factoryModels);

    @Select("SELECT * FROM product WHERE product_name = #{productName} AND deleted = 0 LIMIT 5")
    List<Product> selectByName(String productName);

    @Update("UPDATE product SET id = #{newId} WHERE id = #{oldId}")
    int updateProductId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    @Update("UPDATE bom SET product_id = #{newId} WHERE product_id = #{oldId}")
    int updateBomProductId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    @Update("UPDATE contract_price SET product_id = #{newId} WHERE product_id = #{oldId}")
    int updateContractPriceProductId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    @Update("UPDATE order_oem SET product_id = #{newId} WHERE product_id = #{oldId}")
    int updateOrderOemProductId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    @Update("UPDATE purchase_order_item SET product_id = #{newId} WHERE product_id = #{oldId}")
    int updatePurchaseOrderItemProductId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    @Update("UPDATE sales_pi_item SET product_id = #{newId} WHERE product_id = #{oldId}")
    int updateSalesPiItemProductId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    @Update("UPDATE standard_price SET product_id = #{newId} WHERE product_id = #{oldId}")
    int updateStandardPriceProductId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    @Update("UPDATE stock_movement SET product_id = #{newId} WHERE product_id = #{oldId}")
    int updateStockMovementProductId(@Param("oldId") Long oldId, @Param("newId") Long newId);
}
