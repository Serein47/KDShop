package com.kdshop.catalog.dto;

import java.util.List;
import java.util.Map;

public record ProductDetailView(
        Long id,
        String name,
        String subtitle,
        String description,
        List<String> images,
        Long categoryId,
        String categoryName,
        ShopView shop,
        List<SkuView> skus,
        Map<String, List<TagView>> tags,
        List<OptionView> options,
        String story,
        Integer editionSize,
        Integer editionNo,
        Boolean uniquePiece,
        String flawNote,
        Boolean requiresFlawAck,
        String saleMode,
        Integer leadDays,
        Integer depositRate,
        Integer salesCount,
        Double rating,
        Long reviewCount,
        Boolean favorited
) {
    public record ShopView(Long id, String shopName, String logo) {
    }

    public record SkuView(Long id, Map<String, String> spec, Integer price, Integer originalPrice, Integer stock) {
    }

    public record OptionView(Long id, String name, String type, Boolean required, Integer maxLength,
                             List<String> choices, Integer priceDelta) {
    }
}
