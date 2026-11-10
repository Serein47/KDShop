package com.kdshop.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @Min(value = 1, message = "评分 1-5 星") @Max(value = 5, message = "评分 1-5 星") int rating,
        @NotBlank(message = "写点什么吧") @Size(max = 500, message = "最多 500 字") String content
) {
}
