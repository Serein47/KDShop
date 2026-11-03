package com.kdshop.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApplyRequest(
        @NotBlank(message = "店铺名称不能为空") @Size(max = 64, message = "店铺名称最多 64 字") String shopName,
        @NotBlank(message = "联系人不能为空") @Size(max = 32) String contactName,
        @NotBlank(message = "联系电话不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String contactPhone,
        @Size(max = 500, message = "店铺介绍最多 500 字") String description,
        @Size(max = 255) String licenseImage
) {
}
