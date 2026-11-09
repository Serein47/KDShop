package com.kdshop.admin.dto;

public record AdminCategoryView(
        Long id,
        Long parentId,
        String name,
        Integer sort,
        String status,
        Boolean featured,
        int productCount
) {
}
