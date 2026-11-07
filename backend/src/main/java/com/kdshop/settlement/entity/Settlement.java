package com.kdshop.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("settlements")
public class Settlement {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String settlementNo;
    private Long merchantId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer grossAmountCent;
    private Integer commissionCent;
    private Integer refundCent;
    private Integer netAmountCent;
    private String status;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
