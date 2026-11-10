package com.kdshop.review.dto;

import java.util.List;

public record ProductReviewView(double average, int count, List<ReviewView> items) {
}
