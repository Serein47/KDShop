package com.kdshop.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("product_tags")
public class ProductTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long tagId;
}
