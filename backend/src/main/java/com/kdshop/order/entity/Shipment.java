package com.kdshop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shipments")
public class Shipment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantOrderId;
    private String carrier;
    private String trackingNo;
    private Long operatorId;
    private LocalDateTime shippedAt;
}
