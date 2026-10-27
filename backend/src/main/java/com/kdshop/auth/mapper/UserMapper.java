package com.kdshop.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kdshop.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
