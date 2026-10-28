package com.kdshop.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("product_options")
public class ProductOption {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String name;
    private String type;
    private Integer required;
    private Integer maxLength;
    private String choicesJson;
    private Integer priceDeltaCent;
    private Integer sort;
    private String status;
}
