package com.kdshop.settlement;

import com.kdshop.settlement.entity.Settlement;
import com.kdshop.settlement.entity.SettlementItem;
import com.kdshop.settlement.mapper.SettlementItemMapper;
import com.kdshop.settlement.mapper.SettlementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementReconcileJobTest {

    @Mock private SettlementMapper settlementMapper;
    @Mock private SettlementItemMapper itemMapper;

    private SettlementReconcileJob job;

    @BeforeEach
    void setUp() {
        job = new SettlementReconcileJob(settlementMapper, itemMapper);
    }

    @Test
    void balancedSettlementHasNoDifference() {
        when(settlementMapper.selectList(any())).thenReturn(List.of(settlement(25600, 1280, 0, 500, 23820)));
        when(itemMapper.selectList(any())).thenReturn(List.of(item(25600, 1280, 0, 500, 23820)));

        assertThat(job.reconcile()).isZero();
    }

    @Test
    void itemNetMismatchIsReported() {
        when(settlementMapper.selectList(any())).thenReturn(List.of(settlement(25600, 1280, 0, 500, 23820)));
        when(itemMapper.selectList(any())).thenReturn(List.of(item(25600, 1280, 0, 500, 23000)));

        assertThat(job.reconcile()).isEqualTo(1);
    }

    @Test
    void headerMismatchIsReported() {
        when(settlementMapper.selectList(any())).thenReturn(List.of(settlement(25600, 1280, 0, 500, 99999)));
        when(itemMapper.selectList(any())).thenReturn(List.of(item(25600, 1280, 0, 500, 23820)));

        assertThat(job.reconcile()).isEqualTo(1);
    }

    private Settlement settlement(int gross, int commission, int refund, int penalty, int net) {
        Settlement settlement = new Settlement();
        settlement.setId(1L);
        settlement.setSettlementNo("ST20261116001");
        settlement.setStatus("PENDING");
        settlement.setGrossAmountCent(gross);
        settlement.setCommissionCent(commission);
        settlement.setRefundCent(refund);
        settlement.setPenaltyCent(penalty);
        settlement.setNetAmountCent(net);
        return settlement;
    }

    private SettlementItem item(int gross, int commission, int refund, int penalty, int net) {
        SettlementItem item = new SettlementItem();
        item.setSettlementId(1L);
        item.setMerchantOrderId(9L);
        item.setGrossAmountCent(gross);
        item.setCommissionCent(commission);
        item.setRefundCent(refund);
        item.setPenaltyCent(penalty);
        item.setNetAmountCent(net);
        return item;
    }
}
