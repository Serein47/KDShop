package com.kdshop.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("settlement_items")
public class SettlementItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long settlementId;
    private Long merchantOrderId;
    private Integer grossAmountCent;
    private Integer commissionCent;
    private Integer netAmountCent;
    private LocalDateTime createdAt;
}
