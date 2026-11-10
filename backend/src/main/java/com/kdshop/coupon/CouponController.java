package com.kdshop.coupon;

import com.kdshop.common.ApiResponse;
import com.kdshop.coupon.dto.CouponView;
import com.kdshop.coupon.dto.MyCouponView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/coupons")
    public ApiResponse<List<CouponView>> available() {
        return ApiResponse.ok(couponService.available());
    }

    @PostMapping("/coupons/{id}/claim")
    public ApiResponse<MyCouponView> claim(@PathVariable Long id) {
        return ApiResponse.ok(couponService.claim(id));
    }

    @GetMapping("/me/coupons")
    public ApiResponse<List<MyCouponView>> mine() {
        return ApiResponse.ok(couponService.mine());
    }
}
