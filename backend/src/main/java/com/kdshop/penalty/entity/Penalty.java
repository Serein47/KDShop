package com.kdshop.penalty.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工期超时赔付单：一个子单每天最多一条，V3 的唯一索引兜底。 */
@Data
@TableName("penalties")
public class Penalty {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String penaltyNo;
    private Long merchantOrderId;
    private Long orderId;
    private Long merchantId;
    private Integer daysOverdue;
    private Integer amountCent;
    private String status;
    private LocalDateTime createdAt;
}
