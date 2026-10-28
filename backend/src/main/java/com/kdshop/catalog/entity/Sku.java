package com.kdshop.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("skus")
public class Sku {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String specJson;
    private Integer priceCent;
    private Integer originalPriceCent;
    private Integer stock;
    private Integer lockedStock;
    private String status;
}
