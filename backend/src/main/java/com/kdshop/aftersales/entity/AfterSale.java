package com.kdshop.aftersales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 售后单：一个子单同时最多一条进行中的售后。 */
@Data
@TableName("after_sales")
public class AfterSale {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String asNo;
    private Long merchantOrderId;
    private Long orderId;
    private Long merchantId;
    private Long userId;
    /** REFUND_ONLY / RETURN_REFUND */
    private String type;
    private String reasonCode;
    private String description;
    private String evidenceJson;
    /** APPLIED/APPROVED/REJECTED/RETURNING/RECEIVED/INSPECTED/REFUNDED/CLOSED/ESCALATED */
    private String status;
    private Integer refundAmountCent;
    private String refundNo;
    private String returnCarrier;
    private String returnTrackingNo;
    private LocalDateTime returnShippedAt;
    private LocalDateTime returnDueAt;
    private LocalDateTime receivedAt;
    private LocalDateTime inspectDueAt;
    /** GOOD / DEFECTIVE */
    private String inspectResult;
    private String inspectNote;
    private String inspectImagesJson;
    private Integer stockRestored;
    /** MERCHANT / LOGISTICS / CUSTOMER */
    private String responsibility;
    private String verdictNote;
    private String rejectReason;
    private Integer reapplyCount;
    private String operatorType;
    private Long operatorId;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
