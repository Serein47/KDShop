package com.kdshop.merchant;

import com.kdshop.common.ApiResponse;
import com.kdshop.merchant.dto.MerchantProductView;
import com.kdshop.merchant.dto.ProductStatusRequest;
import com.kdshop.merchant.dto.ProductUpsertRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchant/products")
public class MerchantProductController {

    private final MerchantProductService productService;

    public MerchantProductController(MerchantProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<List<MerchantProductView>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(productService.list(status));
    }

    @PostMapping
    public ApiResponse<MerchantProductView> create(@Valid @RequestBody ProductUpsertRequest request) {
        return ApiResponse.ok(productService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MerchantProductView> update(@PathVariable Long id,
                                                   @Valid @RequestBody ProductUpsertRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<MerchantProductView> status(@PathVariable Long id,
                                                   @Valid @RequestBody ProductStatusRequest request) {
        return ApiResponse.ok(productService.setStatus(id, request.status()));
    }
}
