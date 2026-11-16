package com.kdshop.aftersales;

import com.kdshop.aftersales.dto.AfterSaleView;
import com.kdshop.aftersales.dto.ApplyAfterSaleRequest;
import com.kdshop.aftersales.dto.ReviewAfterSaleRequest;
import com.kdshop.common.ApiResponse;
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
@RequestMapping("/api/v1")
public class AfterSalesController {

    private final AfterSalesService afterSalesService;

    public AfterSalesController(AfterSalesService afterSalesService) {
        this.afterSalesService = afterSalesService;
    }

    @PostMapping("/orders/{orderNo}/sub-orders/{subOrderNo}/after-sales")
    public ApiResponse<AfterSaleView> apply(@PathVariable String orderNo,
                                            @PathVariable String subOrderNo,
                                            @Valid @RequestBody ApplyAfterSaleRequest request) {
        return ApiResponse.ok(afterSalesService.apply(orderNo, subOrderNo, request));
    }

    @GetMapping("/me/after-sales")
    public ApiResponse<List<AfterSaleView>> mine() {
        return ApiResponse.ok(afterSalesService.listMine());
    }

    @GetMapping("/me/after-sales/{asNo}")
    public ApiResponse<AfterSaleView> mineDetail(@PathVariable String asNo) {
        return ApiResponse.ok(afterSalesService.detail(asNo));
    }

    @PostMapping("/me/after-sales/{asNo}/reapply")
    public ApiResponse<AfterSaleView> reapply(@PathVariable String asNo,
                                              @Valid @RequestBody ApplyAfterSaleRequest request) {
        return ApiResponse.ok(afterSalesService.reapply(asNo, request));
    }

    @GetMapping("/merchant/after-sales")
    public ApiResponse<List<AfterSaleView>> merchantList(@RequestParam(required = false) String status) {
        return ApiResponse.ok(afterSalesService.listForMerchant(status));
    }

    @GetMapping("/merchant/after-sales/{asNo}")
    public ApiResponse<AfterSaleView> merchantDetail(@PathVariable String asNo) {
        return ApiResponse.ok(afterSalesService.detail(asNo));
    }

    @PostMapping("/merchant/after-sales/{asNo}/review")
    public ApiResponse<AfterSaleView> review(@PathVariable String asNo,
                                             @Valid @RequestBody ReviewAfterSaleRequest request) {
        return ApiResponse.ok(afterSalesService.review(asNo, request));
    }

    @GetMapping("/admin/after-sales")
    public ApiResponse<List<AfterSaleView>> adminList(@RequestParam(required = false) String status) {
        return ApiResponse.ok(afterSalesService.listAll(status));
    }
}
