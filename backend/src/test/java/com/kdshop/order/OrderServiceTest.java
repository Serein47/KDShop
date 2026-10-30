package com.kdshop.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.cart.mapper.CartItemMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.checkout.CheckoutService;
import com.kdshop.checkout.dto.CheckoutPreview;
import com.kdshop.checkout.dto.CheckoutRequest;
import com.kdshop.checkout.mapper.UserCouponMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.MetaProperties;
import com.kdshop.order.dto.CreateOrderRequest;
import com.kdshop.order.dto.OrderCreatedResult;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private CheckoutService checkoutService;
    @Mock private SkuMapper skuMapper;
    @Mock private CartItemMapper cartItemMapper;
    @Mock private UserCouponMapper userCouponMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private OrderService service;
    private final MetaProperties meta = new MetaProperties("CNY", "Asia/Shanghai", 9900, 1000, 15, 15, 7);

    @BeforeEach
    void setUp() {
        service = new OrderService(checkoutService, skuMapper, cartItemMapper, userCouponMapper,
                orderMapper, merchantOrderMapper, orderItemMapper, redis, meta, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stockLockFailureAbortsBeforePersisting() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(checkoutService.preview(any(CheckoutRequest.class))).thenReturn(preview());
        when(skuMapper.lockStock(any(), any(Integer.class).intValue())).thenReturn(0);

        assertThatThrownBy(() -> service.create(new CreateOrderRequest(null, null, null, null, null, null, null), "k1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);

        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void sameIdempotencyKeyReplaysExistingOrder() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("idem:order:1:k1")).thenReturn("KD202610310001");

        Order order = new Order();
        order.setId(9L);
        order.setOrderNo("KD202610310001");
        order.setStatus("PENDING_PAYMENT");
        order.setPayAmountCent(33400);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(orderMapper.selectOne(any())).thenReturn(order);

        MerchantOrder mo = new MerchantOrder();
        mo.setSubOrderNo("KD202610310001-1");
        mo.setMerchantId(1L);
        mo.setPayAmountCent(33400);
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(mo));

        OrderCreatedResult result = service.create(
                new CreateOrderRequest(null, null, null, null, null, null, null), "k1");

        assertThat(result.orderNo()).isEqualTo("KD202610310001");
        assertThat(result.subOrders()).hasSize(1);
        verify(orderMapper, never()).insert(any(Order.class));
        verify(skuMapper, never()).lockStock(any(), any(Integer.class).intValue());
    }

    private CheckoutPreview preview() {
        CheckoutPreview.Line line = new CheckoutPreview.Line(1L, 1L, "粗陶马克杯", Map.of("颜色", "米白"),
                12800, 2, 25600);
        CheckoutPreview.SubOrder sub = new CheckoutPreview.SubOrder(1L, "山野杂货", List.of(line),
                25600, 0, 0, 25600);
        return new CheckoutPreview(List.of(sub), new CheckoutPreview.Summary(25600, 0, 0, 25600),
                new CheckoutPreview.Coupons(List.of(), List.of()),
                new CheckoutPreview.AddressView("张三", "13800008000", "浙江省", "杭州市", "西湖区", "文三路 1 号"));
    }
}
