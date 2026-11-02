package com.kdshop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_status_logs")
public class OrderStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String scope;
    private Long orderId;
    private Long merchantOrderId;
    private String fromStatus;
    private String toStatus;
    private String operatorType;
    private Long operatorId;
    private String reason;
    private LocalDateTime createdAt;
}
