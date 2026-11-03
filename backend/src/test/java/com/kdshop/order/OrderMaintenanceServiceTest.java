package com.kdshop.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.checkout.mapper.UserCouponMapper;
import com.kdshop.config.MetaProperties;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMaintenanceServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private UserCouponMapper userCouponMapper;
    @Mock private OrderStatusLogMapper statusLogMapper;
    @Mock private EmailOutboxMapper emailOutboxMapper;
    @Mock private TransactionTemplate tx;
    @Mock private TransactionStatus txStatus;

    private OrderMaintenanceService service;
    private final MetaProperties meta = new MetaProperties("CNY", "Asia/Shanghai", 9900, 1000, 15, 7, 7);

    @BeforeEach
    void setUp() {
        service = new OrderMaintenanceService(orderMapper, merchantOrderMapper, orderItemMapper, skuMapper,
                userCouponMapper, statusLogMapper, emailOutboxMapper, meta, tx, new ObjectMapper());
        lenient().when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(txStatus);
        });
    }

    @Test
    void expiredOrderIsCancelledAndStockReleased() {
        Order order = new Order();
        order.setId(5L);
        order.setOrderNo("KD202611030002");
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(orderMapper.update(any(), any())).thenReturn(1);

        OrderItem item = new OrderItem();
        item.setSkuId(1L);
        item.setQuantity(2);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));

        int cancelled = service.cancelExpiredOrders();

        assertThat(cancelled).isEqualTo(1);
        verify(skuMapper).unlockStock(1L, 2);
        verify(emailOutboxMapper).insert(any(EmailOutbox.class));
    }

    @Test
    void alreadyHandledOrderIsSkipped() {
        Order order = new Order();
        order.setId(6L);
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(orderMapper.update(any(), any())).thenReturn(0);

        assertThat(service.cancelExpiredOrders()).isZero();
        verify(skuMapper, org.mockito.Mockito.never()).unlockStock(any(), any(Integer.class).intValue());
    }

    @Test
    void shippedSubOrderIsAutoConfirmed() {
        MerchantOrder sub = new MerchantOrder();
        sub.setId(9L);
        sub.setOrderId(5L);
        sub.setStatus("SHIPPED");
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));
        when(merchantOrderMapper.update(any(), any())).thenReturn(1);
        when(merchantOrderMapper.selectCount(any())).thenReturn(0L);

        assertThat(service.autoConfirmShipped()).isEqualTo(1);
        verify(orderMapper).update(any(), any());
    }
}
