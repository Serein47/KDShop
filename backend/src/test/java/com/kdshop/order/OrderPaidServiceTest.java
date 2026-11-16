package com.kdshop.order;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.mail.MailOutboxWriter;
import com.kdshop.mail.MailTemplateType;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.entity.OrderStatusLog;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import com.kdshop.payment.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaidServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private OrderStatusLogMapper statusLogMapper;
    @Mock private MailOutboxWriter mailOutboxWriter;
    @Mock private ProductMapper productMapper;

    private OrderPaidService service;

    @BeforeEach
    void setUp() {
        service = new OrderPaidService(orderMapper, merchantOrderMapper, orderItemMapper, skuMapper,
                statusLogMapper, mailOutboxWriter, productMapper);
    }

    @Test
    void alreadyPaidOrderIsIgnored() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus("PAID");
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThat(service.markPaid(payment(1L, "PAY1", 12800, "FULL"))).isFalse();
        verify(skuMapper, never()).deductStock(any(), any(Integer.class));
        verify(mailOutboxWriter, never()).enqueueToUser(any(), any(), any(), any());
    }

    @Test
    void pendingOrderIsMarkedPaidAndStockDeducted() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("KD202611020001");
        order.setUserId(1L);
        order.setStatus("PENDING_PAYMENT");
        order.setOrderType("NORMAL");
        order.setPayAmountCent(12800);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(1);

        OrderItem item = new OrderItem();
        item.setSkuId(1L);
        item.setQuantity(2);
        item.setProductName("粗陶马克杯");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(skuMapper.deductStock(1L, 2)).thenReturn(1);

        assertThat(service.markPaid(payment(1L, "PAY1", 12800, "FULL"))).isTrue();

        verify(skuMapper).deductStock(1L, 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(mailOutboxWriter).enqueueToUser(eq(1L), eq(MailTemplateType.PAYMENT_SUCCESS),
                eq("payment_success:PAY1"), payload.capture());
        assertThat(payload.getValue().get("orderNo")).isEqualTo("KD202611020001");
        assertThat(payload.getValue().get("paidAmount")).isEqualTo("128.00");
        assertThat(payload.getValue().get("orderUrl")).isEqualTo("/orders/KD202611020001");
    }

    @Test
    void depositMovesOrderToProducingAndWritesPromisedShipAt() {
        Order order = new Order();
        order.setId(2L);
        order.setOrderNo("KD202611160001");
        order.setUserId(1L);
        order.setStatus("PENDING_PAYMENT");
        order.setOrderType("CUSTOM");
        order.setPayAmountCent(16800);
        order.setDepositCent(5000);
        order.setBalanceCent(11800);
        when(orderMapper.selectById(2L)).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(1);

        MerchantOrder sub = new MerchantOrder();
        sub.setId(5L);
        sub.setOrderId(2L);
        sub.setStatus("PENDING_PAYMENT");
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));
        when(merchantOrderMapper.update(any(), any())).thenReturn(1);

        OrderItem item = new OrderItem();
        item.setMerchantOrderId(5L);
        item.setSkuId(3L);
        item.setProductId(100L);
        item.setQuantity(1);
        item.setProductName("刻字胡桃木盒");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));

        Product product = new Product();
        product.setId(100L);
        product.setLeadDays(14);
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product));
        when(skuMapper.deductStock(3L, 1)).thenReturn(1);

        assertThat(service.markPaid(payment(2L, "PAY2", 5000, "DEPOSIT"))).isTrue();

        verify(skuMapper).deductStock(3L, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Order>> orderUpdate = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(any(), orderUpdate.capture());
        assertThat(orderUpdate.getValue().getSqlSet()).contains("status", "promised_ship_at", "paid_at");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<MerchantOrder>> subUpdate = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(merchantOrderMapper).update(any(), subUpdate.capture());
        assertThat(subUpdate.getValue().getSqlSet()).contains("promised_ship_at");

        ArgumentCaptor<OrderStatusLog> log = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(statusLogMapper).insert(log.capture());
        assertThat(log.getValue().getToStatus()).isEqualTo("PRODUCING");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(mailOutboxWriter).enqueueToUser(eq(1L), eq(MailTemplateType.PAYMENT_SUCCESS),
                eq("payment_success:PAY2"), payload.capture());
        assertThat(payload.getValue().get("paidAmount")).isEqualTo("50.00");
        assertThat(payload.getValue().get("paymentLabel")).isEqualTo("（定金）");
    }

    private Payment payment(Long orderId, String no, int amount, String purpose) {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(orderId);
        payment.setPaymentNo(no);
        payment.setAmountCent(amount);
        payment.setPurpose(purpose);
        return payment;
    }
}
