package com.kdshop.coupon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateCouponRequest(
        @Size(max = 32) String code,
        @NotBlank(message = "优惠券名称不能为空") @Size(max = 64) String name,
        @NotBlank(message = "请选择类型") String type,
        @NotNull(message = "请填写面额") @Min(value = 1, message = "面额必须大于 0") Integer value,
        @NotNull(message = "请填写门槛") @Min(0) Integer minAmount,
        @NotNull(message = "请填写发行量") @Min(1) Integer total,
        @NotNull(message = "请选择开始时间") LocalDateTime startsAt,
        @NotNull(message = "请选择结束时间") LocalDateTime endsAt
) {
}
