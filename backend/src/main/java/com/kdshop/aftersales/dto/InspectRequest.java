package com.kdshop.aftersales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InspectRequest(
        @NotBlank(message = "请选择质检结果") String result,
        @Size(max = 300, message = "质检说明最多 300 字") String note,
        @Size(max = 3, message = "最多 3 张质检图片") List<@Size(max = 255) String> images
) {
}
