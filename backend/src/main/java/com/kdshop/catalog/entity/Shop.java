package com.kdshop.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("merchants")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String shopName;
    private String logo;
    private String description;
    private String contactName;
    private String contactPhone;
    private String licenseImage;
    private String status;
    private String rejectReason;
    private String suspendReason;
    private Integer commissionRate;
    private LocalDateTime approvedAt;
    private Long approvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
