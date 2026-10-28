package com.kdshop.catalog.dto;

import java.time.LocalDateTime;

public record ShopPage(Long id, String shopName, String logo, String description,
                       Integer productCount, Integer salesCount, LocalDateTime createdAt) {
}
