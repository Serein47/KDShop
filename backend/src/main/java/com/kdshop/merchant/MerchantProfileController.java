package com.kdshop.merchant;

import com.kdshop.common.ApiResponse;
import com.kdshop.merchant.dto.DashboardView;
import com.kdshop.merchant.dto.ShopProfileRequest;
import com.kdshop.merchant.dto.ShopProfileView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant")
public class MerchantProfileController {

    private final MerchantService merchantService;
    private final MerchantDashboardService dashboardService;

    public MerchantProfileController(MerchantService merchantService, MerchantDashboardService dashboardService) {
        this.merchantService = merchantService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/profile")
    public ApiResponse<ShopProfileView> profile() {
        return ApiResponse.ok(merchantService.myProfile());
    }

    @PutMapping("/profile")
    public ApiResponse<ShopProfileView> update(@Valid @RequestBody ShopProfileRequest request) {
        return ApiResponse.ok(merchantService.updateProfile(request));
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardView> dashboard() {
        return ApiResponse.ok(dashboardService.dashboard());
    }
}
