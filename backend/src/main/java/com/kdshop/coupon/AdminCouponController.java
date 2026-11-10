package com.kdshop.coupon;

import com.kdshop.common.ApiResponse;
import com.kdshop.coupon.dto.CouponView;
import com.kdshop.coupon.dto.CreateCouponRequest;
import com.kdshop.coupon.dto.GrantRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/coupons")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public ApiResponse<List<CouponView>> list() {
        return ApiResponse.ok(couponService.adminList());
    }

    @PostMapping
    public ApiResponse<CouponView> create(@Valid @RequestBody CreateCouponRequest request) {
        return ApiResponse.ok(couponService.create(request));
    }

    @PostMapping("/{id}/grant")
    public ApiResponse<CouponService.GrantResult> grant(@PathVariable Long id, @RequestBody GrantRequest request) {
        return ApiResponse.ok(couponService.grant(id, request.emails(), request.allUsers()));
    }
}
