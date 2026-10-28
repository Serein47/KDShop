package com.kdshop.cart;

import com.kdshop.cart.dto.CartItemPatch;
import com.kdshop.cart.dto.CartItemRequest;
import com.kdshop.cart.dto.CartItemView;
import com.kdshop.cart.dto.CartView;
import com.kdshop.cart.dto.MergeRequest;
import com.kdshop.cart.dto.MergeResult;
import com.kdshop.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartView> view() {
        return ApiResponse.ok(cartService.view());
    }

    @PostMapping("/items")
    public ApiResponse<CartItemView> add(@Valid @RequestBody CartItemRequest request) {
        return ApiResponse.ok(cartService.add(request.skuId(), request.quantity()));
    }

    @PutMapping("/items/{id}")
    public ApiResponse<Map<String, Boolean>> update(@PathVariable Long id,
                                                    @Valid @RequestBody CartItemPatch patch) {
        cartService.update(id, patch.quantity(), patch.selected());
        return ApiResponse.ok(Map.of("updated", true));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Map<String, Boolean>> remove(@PathVariable Long id) {
        cartService.remove(id);
        return ApiResponse.ok(Map.of("removed", true));
    }

    @PostMapping("/merge")
    public ApiResponse<MergeResult> merge(@Valid @RequestBody MergeRequest request) {
        return ApiResponse.ok(cartService.merge(request));
    }
}
