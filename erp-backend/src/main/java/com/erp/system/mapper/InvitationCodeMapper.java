package com.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.entity.InvitationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InvitationCodeMapper extends BaseMapper<InvitationCode> {

    /** Atomically consumes one use so concurrent registrations cannot exceed the limit. */
    @Update("""
            UPDATE sys_invitation_code
            SET used_count = used_count + 1, update_time = NOW()
            WHERE id = #{id}
              AND deleted = 0
              AND (expire_time IS NULL OR expire_time > NOW())
              AND used_count < max_uses
            """)
    int consume(@Param("id") Long id);
}
