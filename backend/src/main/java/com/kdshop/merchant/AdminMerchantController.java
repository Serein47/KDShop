package com.kdshop.merchant;

import com.kdshop.common.ApiResponse;
import com.kdshop.merchant.dto.MerchantView;
import com.kdshop.merchant.dto.RejectRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/merchants")
public class AdminMerchantController {

    private final MerchantService merchantService;

    public AdminMerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public ApiResponse<List<MerchantView>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(merchantService.list(status));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<MerchantView> approve(@PathVariable Long id) {
        return ApiResponse.ok(merchantService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<MerchantView> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return ApiResponse.ok(merchantService.reject(id, request.reason()));
    }
}
