package com.erp.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.basedata.entity.Bom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BomMapper extends BaseMapper<Bom> {

    @Select("SELECT bom_code FROM bom WHERE bom_code IS NOT NULL")
    List<String> selectAllCodes();

    @Update("""
            UPDATE bom
            SET bom_code = CONCAT('__DELETED__', id),
                update_time = NOW()
            WHERE bom_code = #{bomCode}
              AND deleted = 1
            """)
    int archiveDeletedCodeConflicts(@Param("bomCode") String bomCode);
}
