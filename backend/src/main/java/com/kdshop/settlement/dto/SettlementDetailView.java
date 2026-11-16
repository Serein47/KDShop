package com.kdshop.settlement.dto;

import java.util.List;

/** 结算单详情：表头 + 每个子单一行明细（含退款与赔付扣减）。 */
public record SettlementDetailView(SettlementView settlement, List<SettlementItemView> items) {
}
