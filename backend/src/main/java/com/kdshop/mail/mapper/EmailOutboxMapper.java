package com.kdshop.mail.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kdshop.mail.entity.EmailOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailOutboxMapper extends BaseMapper<EmailOutbox> {
}
