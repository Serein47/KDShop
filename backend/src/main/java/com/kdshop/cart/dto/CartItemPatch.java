package com.kdshop.cart.dto;

import jakarta.validation.constraints.Min;

public record CartItemPatch(
        @Min(value = 0, message = "数量不能为负") Integer quantity,
        Boolean selected
) {
}
