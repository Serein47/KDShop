package com.kdshop.catalog.dto;

/** 列表卡片：不返回库存明细与描述，控制载荷。 */
public record ProductCard(
        Long id,
        String name,
        String subtitle,
        String mainImage,
        Integer minPrice,
        Integer maxPrice,
        Integer salesCount,
        Long merchantId,
        String shopName,
        String saleMode,
        Integer leadDays,
        Integer depositRate,
        Boolean uniquePiece,
        Integer editionSize,
        Integer editionNo
) {
}
