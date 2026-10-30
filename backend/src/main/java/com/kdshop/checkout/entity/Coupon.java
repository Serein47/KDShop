package com.kdshop.checkout.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("coupons")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String type;
    private Integer valueCent;
    private Integer minAmountCent;
    private Integer total;
    private Integer usedCount;
    private String scope;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String status;
}
