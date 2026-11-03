package com.kdshop.order;

import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.checkout.mapper.UserCouponMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.order.dto.OrderCancelResult;
import com.kdshop.order.dto.SubOrderConfirmResult;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.entity.OrderStatusLog;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import com.kdshop.payment.mapper.PaymentMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderActionServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderStatusLogMapper statusLogMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private UserCouponMapper userCouponMapper;
    @Mock private PaymentMapper paymentMapper;

    private OrderActionService service;

    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, MerchantOrder.class);
        TableInfoHelper.initTableInfo(assistant, com.kdshop.checkout.entity.UserCoupon.class);
        TableInfoHelper.initTableInfo(assistant, com.kdshop.payment.entity.Payment.class);
    }

    @BeforeEach
    void setUp() {
        service = new OrderActionService(orderMapper, merchantOrderMapper, orderItemMapper,
                statusLogMapper, skuMapper, userCouponMapper, paymentMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cancelPendingOrderReleasesStockCouponAndPayment() {
        Order order = order(10L, "KD202611030001", 1L, "PENDING_PAYMENT");
        order.setCouponId(5L);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(1);

        OrderItem first = item(8L, 1);
        OrderItem second = item(13L, 2);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(skuMapper.unlockStock(8L, 1)).thenReturn(1);
        when(skuMapper.unlockStock(13L, 2)).thenReturn(1);

        OrderCancelResult result = service.cancel("KD202611030001");

        assertThat(result.status()).isEqualTo("CANCELLED");
        verify(skuMapper).unlockStock(8L, 1);
        verify(skuMapper).unlockStock(13L, 2);
        verify(merchantOrderMapper).update(any(), any());
        verify(userCouponMapper).update(any(), any());
        verify(paymentMapper).update(any(), any());

        ArgumentCaptor<OrderStatusLog> captor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(statusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getToStatus()).isEqualTo("CANCELLED");
        assertThat(captor.getValue().getOperatorType()).isEqualTo("USER");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(1L);
    }

    @Test
    void cancelPaidOrderIsRejectedWithoutSideEffects() {
        Order order = order(10L, "KD202611030001", 1L, "PAID");
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertThatThrownBy(() -> service.cancel("KD202611030001"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.ORDER_STATE_INVALID);

        verify(orderMapper, never()).update(any(), any());
        verify(skuMapper, never()).unlockStock(any(), anyInt());
    }

    @Test
    void cancelOthersOrderIsForbidden() {
        Order order = order(10L, "KD202611030001", 99L, "PENDING_PAYMENT");
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertThatThrownBy(() -> service.cancel("KD202611030001"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void confirmLastShippedSubOrderCompletesMainOrder() {
        Order order = order(3L, "KD202610010003", 1L, "SHIPPED");
        when(orderMapper.selectOne(any())).thenReturn(order);

        MerchantOrder sub = sub(3L, "KD202610010003-1", "SHIPPED");
        when(merchantOrderMapper.selectOne(any())).thenReturn(sub);
        when(merchantOrderMapper.update(any(), any())).thenReturn(1);

        MerchantOrder completedFirst = sub(3L, "KD202610010003-1", "COMPLETED");
        MerchantOrder completedSecond = sub(4L, "KD202610010003-2", "COMPLETED");
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(completedFirst, completedSecond));
        when(orderMapper.update(any(), any())).thenReturn(1);

        SubOrderConfirmResult result = service.confirm("KD202610010003", "KD202610010003-1");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.orderStatus()).isEqualTo("COMPLETED");
        verify(merchantOrderMapper).update(any(), any());
        verify(orderMapper).update(any(), any());
        verify(statusLogMapper, org.mockito.Mockito.times(2)).insert(any(OrderStatusLog.class));
    }

    @Test
    void confirmAlreadyCompletedSubOrderIsIdempotent() {
        Order order = order(3L, "KD202610010003", 1L, "COMPLETED");
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(merchantOrderMapper.selectOne(any())).thenReturn(sub(3L, "KD202610010003-1", "COMPLETED"));

        SubOrderConfirmResult result = service.confirm("KD202610010003", "KD202610010003-1");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.orderStatus()).isEqualTo("COMPLETED");
        verify(merchantOrderMapper, never()).update(any(), any());
        verify(statusLogMapper, never()).insert(any(OrderStatusLog.class));
    }

    @Test
    void confirmPendingShipmentSubOrderIsRejected() {
        Order order = order(3L, "KD202610010003", 1L, "PAID");
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(merchantOrderMapper.selectOne(any())).thenReturn(sub(3L, "KD202610010003-1", "PENDING_SHIPMENT"));

        assertThatThrownBy(() -> service.confirm("KD202610010003", "KD202610010003-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SUB_ORDER_STATE_INVALID);

        verify(merchantOrderMapper, never()).update(any(), any());
    }

    private Order order(Long id, String no, Long userId, String status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo(no);
        order.setUserId(userId);
        order.setStatus(status);
        order.setPayAmountCent(19700);
        order.setCouponId(null);
        return order;
    }

    private MerchantOrder sub(Long id, String no, String status) {
        MerchantOrder sub = new MerchantOrder();
        sub.setId(id);
        sub.setSubOrderNo(no);
        sub.setOrderId(3L);
        sub.setMerchantId(1L);
        sub.setStatus(status);
        return sub;
    }

    private OrderItem item(Long skuId, int quantity) {
        OrderItem item = new OrderItem();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        item.setProductName("商品" + skuId);
        return item;
    }
}
