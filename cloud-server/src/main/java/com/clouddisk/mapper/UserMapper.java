package com.clouddisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clouddisk.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT id, openid, nickname, avatar, create_time, update_time FROM `user` WHERE openid = #{openid}")
    User selectByOpenid(@Param("openid") String openid);
}
