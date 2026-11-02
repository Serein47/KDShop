package com.kdshop.mail.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_outbox")
public class EmailOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String toEmail;
    private String template;
    private String bizKey;
    private String payloadJson;
    private String status;
    private String error;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
