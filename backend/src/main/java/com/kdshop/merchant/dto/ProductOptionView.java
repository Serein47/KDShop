package com.kdshop.merchant.dto;

import java.util.List;

public record ProductOptionView(
        Long id,
        String name,
        String type,
        boolean required,
        Integer maxLength,
        List<String> choices,
        int priceDelta,
        int sort
) {
}
