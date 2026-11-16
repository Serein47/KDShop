package com.kdshop.penalty;

import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.penalty.entity.Penalty;
import com.kdshop.penalty.mapper.PenaltyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PenaltyServiceTest {

    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private PenaltyMapper penaltyMapper;

    private PenaltyService service;

    @BeforeEach
    void setUp() {
        service = new PenaltyService(merchantOrderMapper, orderMapper, penaltyMapper);
    }

    @Test
    void splitsOrderDepositAcrossSubOrdersByPayAmount() {
        Order order = order(3000, 20000);
        MerchantOrder sub = sub(9L, 10L, 10000, LocalDateTime.now().minusDays(2));
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));
        when(orderMapper.selectBatchIds(any())).thenReturn(List.of(order));
        when(penaltyMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.generate()).isEqualTo(2);

        ArgumentCaptor<Penalty> captor = ArgumentCaptor.forClass(Penalty.class);
        verify(penaltyMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(Penalty::getAmountCent).containsExactly(75, 75);
        assertThat(captor.getAllValues()).extracting(Penalty::getDaysOverdue).containsExactly(1, 2);
        assertThat(service.depositBase(order, sub)).isEqualTo(1500);
    }

    @Test
    void capsAtHalfOfSubOrderDeposit() {
        Order order = order(5000, 16800);
        MerchantOrder sub = sub(9L, 10L, 16800, LocalDateTime.now().minusDays(20));
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));
        when(orderMapper.selectBatchIds(any())).thenReturn(List.of(order));
        when(penaltyMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.generate()).isEqualTo(10);

        ArgumentCaptor<Penalty> captor = ArgumentCaptor.forClass(Penalty.class);
        verify(penaltyMapper, org.mockito.Mockito.times(10)).insert(captor.capture());
        int total = captor.getAllValues().stream().mapToInt(Penalty::getAmountCent).sum();
        assertThat(total).isEqualTo(2500);
    }

    @Test
    void backfillsMissingDaysOnly() {
        Order order = order(5000, 16800);
        MerchantOrder sub = sub(9L, 10L, 16800, LocalDateTime.now().minusDays(3));
        Penalty existing = new Penalty();
        existing.setMerchantOrderId(9L);
        existing.setDaysOverdue(1);
        existing.setAmountCent(250);
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));
        when(orderMapper.selectBatchIds(any())).thenReturn(List.of(order));
        when(penaltyMapper.selectList(any())).thenReturn(List.of(existing));

        assertThat(service.generate()).isEqualTo(2);

        ArgumentCaptor<Penalty> captor = ArgumentCaptor.forClass(Penalty.class);
        verify(penaltyMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(Penalty::getDaysOverdue).containsExactly(2, 3);
    }

    @Test
    void alreadyUpToDateSubOrderGeneratesNothing() {
        Order order = order(5000, 16800);
        MerchantOrder sub = sub(9L, 10L, 16800, LocalDateTime.now().minusDays(2));
        Penalty existing = new Penalty();
        existing.setMerchantOrderId(9L);
        existing.setDaysOverdue(2);
        existing.setAmountCent(500);
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));
        when(orderMapper.selectBatchIds(any())).thenReturn(List.of(order));
        when(penaltyMapper.selectList(any())).thenReturn(List.of(existing));

        assertThat(service.generate()).isZero();
        verify(penaltyMapper, never()).insert(any(Penalty.class));
    }

    @Test
    void shippedSubOrderIsNotPenalised() {
        MerchantOrder sub = sub(9L, 10L, 16800, LocalDateTime.now().minusDays(3));
        sub.setShippedAt(LocalDateTime.now().minusDays(1));
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));
        when(orderMapper.selectBatchIds(any())).thenReturn(List.of(order(5000, 16800)));

        assertThat(service.generate()).isZero();
        verify(penaltyMapper, never()).insert(any(Penalty.class));
    }

    private Order order(int depositCent, int payAmountCent) {
        Order order = new Order();
        order.setId(10L);
        order.setDepositCent(depositCent);
        order.setBalanceCent(payAmountCent - depositCent);
        order.setPayAmountCent(payAmountCent);
        return order;
    }

    private MerchantOrder sub(Long id, Long orderId, int payAmountCent, LocalDateTime promisedShipAt) {
        MerchantOrder sub = new MerchantOrder();
        sub.setId(id);
        sub.setOrderId(orderId);
        sub.setMerchantId(1L);
        sub.setStatus("PRODUCING");
        sub.setPayAmountCent(payAmountCent);
        sub.setPromisedShipAt(promisedShipAt);
        return sub;
    }
}
