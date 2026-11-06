package com.kdshop.merchant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductUpsertRequest(
        @NotBlank(message = "商品名称不能为空") @Size(max = 128) String name,
        @Size(max = 200) String subtitle,
        String description,
        @NotNull(message = "请选择类目") Long categoryId,
        @NotNull(message = "请填写价格") @Min(value = 1, message = "价格必须大于 0") Integer price,
        @NotNull(message = "请填写库存") @Min(value = 0, message = "库存不能为负") Integer stock,
        @Size(max = 255) String mainImage
) {
}
