package com.kdshop.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("products")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String description;
    private String mainImage;
    private String imagesJson;
    private String status;
    private String offlineReason;
    private Integer salesCount;
    private String saleMode;
    private Integer leadDays;
    private Integer depositRate;
    private String story;
    private Integer editionSize;
    private Integer editionNo;
    private Integer isUnique;
    private String flawNote;
    private Integer requiresFlawAck;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
