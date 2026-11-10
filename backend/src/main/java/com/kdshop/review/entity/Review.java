package com.kdshop.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reviews")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderItemId;
    private Long userId;
    private Long productId;
    private Long merchantId;
    private Integer rating;
    private String content;
    private String status;
    private LocalDateTime createdAt;
}
