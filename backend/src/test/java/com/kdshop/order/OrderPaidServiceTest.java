package com.kdshop.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private EmailOutboxMapper emailOutboxMapper;
    @Mock private UserMapper userMapper;

    private OrderPaidService service;

    @BeforeEach
    void setUp() {
        service = new OrderPaidService(orderMapper, merchantOrderMapper, orderItemMapper, skuMapper,
                statusLogMapper, emailOutboxMapper, new ObjectMapper(), userMapper);
    }

    @Test
    void alreadyPaidOrderIsIgnored() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus("PAID");
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThat(service.markPaid(1L, "PAY1")).isFalse();
        verify(skuMapper, never()).deductStock(any(), any(Integer.class));
        verify(emailOutboxMapper, never()).insert(any(EmailOutbox.class));
    }

    @Test
    void pendingOrderIsMarkedPaidAndStockDeducted() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("KD202611020001");
        order.setUserId(1L);
        order.setStatus("PENDING_PAYMENT");
        order.setPayAmountCent(12800);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(1);

        OrderItem item = new OrderItem();
        item.setSkuId(1L);
        item.setQuantity(2);
        item.setProductName("粗陶马克杯");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(skuMapper.deductStock(1L, 2)).thenReturn(1);

        User user = new User();
        user.setId(1L);
        user.setEmail("customer@kdshop.dev");
        when(userMapper.selectById(1L)).thenReturn(user);

        assertThat(service.markPaid(1L, "PAY1")).isTrue();

        verify(skuMapper).deductStock(1L, 2);

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(emailOutboxMapper).insert(captor.capture());
        assertThat(captor.getValue().getBizKey()).isEqualTo("payment_success:PAY1");
        assertThat(captor.getValue().getToEmail()).isEqualTo("customer@kdshop.dev");
    }
}
