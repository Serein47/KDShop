package com.kdshop.review.dto;

import java.time.LocalDateTime;

public record ReviewView(Long id, String nickname, int rating, String content, LocalDateTime createdAt) {
}
