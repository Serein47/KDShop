package com.kdshop.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kdshop.auth.entity.EmailOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailOutboxMapper extends BaseMapper<EmailOutbox> {
}
