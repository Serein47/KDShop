package com.kdshop.merchant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 商家定义商品定制项：文本或单选，可设必填、字数上限与加价。 */
public record ProductOptionRequest(
        @NotBlank(message = "定制项名称不能为空") @Size(max = 32) String name,
        @NotBlank(message = "请选择定制项类型") @Pattern(regexp = "TEXT|ENUM", message = "类型只能是 TEXT 或 ENUM")
        String type,
        Boolean required,
        @Min(value = 1, message = "字数上限至少 1") Integer maxLength,
        List<@Size(max = 32) String> choices,
        @Min(value = 0, message = "加价不能为负") Integer priceDelta,
        Integer sort
) {
}
