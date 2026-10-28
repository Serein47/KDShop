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
    private String shopName;
    private String logo;
    private String description;
    private String status;
    private Integer commissionRate;
    private LocalDateTime createdAt;
}
