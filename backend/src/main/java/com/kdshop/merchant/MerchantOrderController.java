package com.kdshop.merchant;

import com.kdshop.common.ApiResponse;
import com.kdshop.merchant.dto.ShipRequest;
import com.kdshop.merchant.dto.SubOrderView;
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
@RequestMapping("/api/v1/merchant/sub-orders")
public class MerchantOrderController {

    private final MerchantOrderService merchantOrderService;

    public MerchantOrderController(MerchantOrderService merchantOrderService) {
        this.merchantOrderService = merchantOrderService;
    }

    @GetMapping
    public ApiResponse<List<SubOrderView>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(merchantOrderService.list(status));
    }

    @PostMapping("/{subOrderNo}/ship")
    public ApiResponse<SubOrderView> ship(@PathVariable String subOrderNo,
                                          @Valid @RequestBody ShipRequest request) {
        return ApiResponse.ok(merchantOrderService.ship(subOrderNo, request));
    }
}
