package com.kdshop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("merchant_orders")
public class MerchantOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String subOrderNo;
    private Long orderId;
    private Long merchantId;
    private String status;
    private Integer goodsAmountCent;
    private Integer discountCent;
    private Integer shippingFeeCent;
    private Integer payAmountCent;
    private Integer refundedAmountCent;
    private LocalDateTime shippedAt;
    private LocalDateTime completedAt;
    private Integer settled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
