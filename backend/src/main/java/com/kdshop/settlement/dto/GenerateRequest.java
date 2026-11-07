package com.kdshop.settlement.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerateRequest(
        @NotNull(message = "请选择开始日期") LocalDate periodStart,
        @NotNull(message = "请选择结束日期") LocalDate periodEnd
) {
}
