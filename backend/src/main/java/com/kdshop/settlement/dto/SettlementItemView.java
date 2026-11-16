package com.kdshop.settlement.dto;

import java.util.List;

public record SettlementItemView(
        String subOrderNo,
        int grossAmount,
        int commission,
        int refund,
        int penalty,
        int netAmount,
        List<RefundLineView> refunds,
        List<PenaltyLineView> penalties
) {
}
