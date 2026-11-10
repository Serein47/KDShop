package com.kdshop.review.dto;

import java.time.LocalDateTime;

public record MyReviewView(Long orderItemId, Long productId, int rating, String content, LocalDateTime createdAt) {
}
