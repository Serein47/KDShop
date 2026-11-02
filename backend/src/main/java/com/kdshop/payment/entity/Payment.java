package com.kdshop.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("payments")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentNo;
    private Long orderId;
    private String channel;
    private Integer amountCent;
    private String status;
    private String outTradeNo;
    private String orderNo;
    private String purpose;
    private LocalDateTime paidAt;
    private LocalDateTime callbackAt;
    private String callbackRaw;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
