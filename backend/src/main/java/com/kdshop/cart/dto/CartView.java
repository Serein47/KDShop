package com.kdshop.cart.dto;

import java.util.List;

public record CartView(List<CartGroup> groups, Summary summary) {
    public record Summary(int selectedCount, int goodsAmount) {
    }
}
