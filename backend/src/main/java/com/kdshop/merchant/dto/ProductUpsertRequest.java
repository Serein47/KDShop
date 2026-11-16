package com.kdshop.merchant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 商家新建/编辑商品。
 *
 * <p>销售模式（ISSUE-33）：{@code IN_STOCK} 现货、{@code PREORDER} 预售、{@code CUSTOM} 定制。
 * 预售与定制必须同时给出 {@code leadDays}（3–60 天）与 {@code depositRate}（定金比例，基点，
 * 3000 = 30%）；现货忽略这两项。SKU 不单独存销售模式，一律继承商品。</p>
 */
public record ProductUpsertRequest(
        @NotBlank(message = "商品名称不能为空") @Size(max = 128) String name,
        @Size(max = 200) String subtitle,
        String description,
        @NotNull(message = "请选择类目") Long categoryId,
        @NotNull(message = "请填写价格") @Min(value = 1, message = "价格必须大于 0") Integer price,
        @NotNull(message = "请填写库存") @Min(value = 0, message = "库存不能为负") Integer stock,
        @Size(max = 255) String mainImage,
        @Pattern(regexp = "IN_STOCK|PREORDER|CUSTOM", message = "销售模式只能是 IN_STOCK / PREORDER / CUSTOM")
        String saleMode,
        // 范围在 Service 里按销售模式判断：现货忽略这两项，预售/定制才强制 3–60 天与 1%–100%
        Integer leadDays,
        Integer depositRate
) {
}
