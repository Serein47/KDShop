package com.kdshop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private String status;
    private Integer merchantCount;
    private Integer goodsAmountCent;
    private Integer discountCent;
    private Integer shippingFeeCent;
    private Integer payAmountCent;
    private String addressJson;
    private Long couponId;
    private String remark;
    private String orderType;
    private Integer depositCent;
    private Integer balanceCent;
    private LocalDateTime promisedShipAt;
    private Integer penaltyCent;
    private Integer isGift;
    private String giftMessage;
    private Integer giftWrap;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
