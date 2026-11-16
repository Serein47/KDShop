package com.kdshop.aftersales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 售后状态流水：每次流转都记操作者与说明，自动裁决记为 SYSTEM。 */
@Data
@TableName("after_sales_logs")
public class AfterSaleLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long afterSalesId;
    private String fromStatus;
    private String toStatus;
    /** USER / MERCHANT / ADMIN / SYSTEM */
    private String operatorType;
    private Long operatorId;
    private String note;
    private LocalDateTime createdAt;
}
