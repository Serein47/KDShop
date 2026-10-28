package com.kdshop.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tags")
public class Tag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dimension;
    private String name;
    private String slug;
    private Integer sort;
    private String status;
}
