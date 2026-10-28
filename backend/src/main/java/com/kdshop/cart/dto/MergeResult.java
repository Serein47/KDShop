package com.kdshop.cart.dto;

import java.util.List;

public record MergeResult(int merged, List<Dropped> dropped) {
    public record Dropped(Long skuId, String reason, Integer available) {
    }
}
