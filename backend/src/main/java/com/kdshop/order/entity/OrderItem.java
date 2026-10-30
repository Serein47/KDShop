package com.kdshop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_items")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantOrderId;
    private Long orderId;
    private Long merchantId;
    private Long skuId;
    private Long productId;
    private String productName;
    private String shopName;
    private String skuSpec;
    private Integer unitPriceCent;
    private Integer quantity;
    private Integer subtotalCent;
    private LocalDateTime createdAt;
}
