package com.kdshop.cart.dto;

import java.util.List;

public record CartGroup(Long merchantId, String shopName, List<CartItemView> items, Integer subtotal) {
}
