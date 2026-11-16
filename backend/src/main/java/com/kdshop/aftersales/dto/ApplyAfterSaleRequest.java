package com.kdshop.aftersales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApplyAfterSaleRequest(
        @NotBlank(message = "请选择售后类型") String type,
        @NotBlank(message = "请选择售后原因") String reasonCode,
        @Size(max = 500, message = "问题描述最多 500 字") String description,
        @Size(max = 3, message = "最多上传 3 张凭证") List<@Size(max = 255) String> evidence
) {
}
