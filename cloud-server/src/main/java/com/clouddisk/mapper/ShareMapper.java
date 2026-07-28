package com.clouddisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clouddisk.entity.Share;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ShareMapper extends BaseMapper<Share> {

    @Select("""
            SELECT *
            FROM share
            WHERE share_code = #{shareCode}
            LIMIT 1
            """)
    Share selectByShareCode(@Param("shareCode") String shareCode);
}
