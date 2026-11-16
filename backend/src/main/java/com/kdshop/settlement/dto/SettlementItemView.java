package com.kdshop.settlement.dto;

public record SettlementItemView(
        String subOrderNo,
        int grossAmount,
        int commission,
        int refund,
        int penalty,
        int netAmount
) {
}
