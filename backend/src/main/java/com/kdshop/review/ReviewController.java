package com.kdshop.review;

import com.kdshop.common.ApiResponse;
import com.kdshop.review.dto.CreateReviewRequest;
import com.kdshop.review.dto.MyReviewView;
import com.kdshop.review.dto.ProductReviewView;
import com.kdshop.review.dto.ReviewView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/order-items/{id}/reviews")
    public ApiResponse<ReviewView> create(@PathVariable Long id, @Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.ok(reviewService.create(id, request));
    }

    @GetMapping("/products/{id}/reviews")
    public ApiResponse<ProductReviewView> byProduct(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.byProduct(id));
    }

    @GetMapping("/me/reviews")
    public ApiResponse<List<MyReviewView>> mine() {
        return ApiResponse.ok(reviewService.mine());
    }
}
