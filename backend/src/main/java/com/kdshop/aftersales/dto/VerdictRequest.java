package com.kdshop.aftersales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 平台强制裁决：决定 + 责任归属 + 必填原因。 */
public record VerdictRequest(
        @NotBlank(message = "请选择裁决结果") String decision,
        @NotBlank(message = "请选择责任归属") String responsibility,
        @NotBlank(message = "平台裁决必须填写原因") @Size(max = 300) String note
) {
}
