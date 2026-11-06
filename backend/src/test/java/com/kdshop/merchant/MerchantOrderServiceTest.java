package com.kdshop.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
import com.kdshop.merchant.dto.ShipRequest;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantOrderServiceTest {

    @Mock private ShopMapper shopMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderStatusLogMapper statusLogMapper;
    @Mock private EmailOutboxMapper emailOutboxMapper;
    @Mock private UserMapper userMapper;

    private MerchantOrderService service;

    @BeforeEach
    void setUp() {
        service = new MerchantOrderService(shopMapper, merchantOrderMapper, orderMapper, orderItemMapper,
                statusLogMapper, emailOutboxMapper, userMapper, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(3L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shippingAnotherShopsSubOrderIsForbidden() {
        when(shopMapper.selectOne(any())).thenReturn(shop(1L));
        MerchantOrder sub = new MerchantOrder();
        sub.setId(9L);
        sub.setSubOrderNo("KD1-2");
        sub.setMerchantId(2L);
        sub.setStatus("PENDING_SHIPMENT");
        when(merchantOrderMapper.selectOne(any())).thenReturn(sub);

        assertThatThrownBy(() -> service.ship("KD1-2", new ShipRequest("顺丰速运", "SF1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MERCHANT_SCOPE_FORBIDDEN);

        verify(merchantOrderMapper, never()).update(any(), any());
    }

    @Test
    void shippedSubOrderCannotBeShippedAgain() {
        when(shopMapper.selectOne(any())).thenReturn(shop(1L));
        MerchantOrder sub = new MerchantOrder();
        sub.setId(9L);
        sub.setMerchantId(1L);
        sub.setStatus("SHIPPED");
        when(merchantOrderMapper.selectOne(any())).thenReturn(sub);

        assertThatThrownBy(() -> service.ship("KD1-1", new ShipRequest("顺丰速运", "SF1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SUB_ORDER_STATE_INVALID);
    }

    @Test
    void shippingWritesMailAndStatusLog() {
        when(shopMapper.selectOne(any())).thenReturn(shop(1L));
        MerchantOrder sub = new MerchantOrder();
        sub.setId(9L);
        sub.setOrderId(5L);
        sub.setMerchantId(1L);
        sub.setSubOrderNo("KD1-1");
        sub.setStatus("PENDING_SHIPMENT");
        sub.setPayAmountCent(12800);
        sub.setGoodsAmountCent(12800);
        sub.setDiscountCent(0);
        sub.setShippingFeeCent(0);
        sub.setCarrier("顺丰速运");
        sub.setTrackingNo("SF1");
        when(merchantOrderMapper.selectOne(any())).thenReturn(sub);
        when(merchantOrderMapper.update(any(), any())).thenReturn(1);

        Order order = new Order();
        order.setId(5L);
        order.setOrderNo("KD1");
        order.setUserId(1L);
        order.setStatus("PAID");
        order.setAddressJson("{\"receiver\":\"张三\",\"phone\":\"13800008000\",\"province\":\"浙江省\",\"city\":\"杭州市\",\"district\":\"西湖区\",\"detail\":\"文三路 1 号\"}");
        when(orderMapper.selectById(5L)).thenReturn(order);
        when(merchantOrderMapper.selectCount(any())).thenReturn(1L);
        when(merchantOrderMapper.selectById(9L)).thenReturn(sub);
        when(orderItemMapper.selectList(any())).thenReturn(List.of());

        var view = service.ship("KD1-1", new ShipRequest("顺丰速运", "SF1"));

        assertThat(view.subOrderNo()).isEqualTo("KD1-1");
        verify(emailOutboxMapper).insert(any(EmailOutbox.class));
        verify(statusLogMapper).insert(any(com.kdshop.order.entity.OrderStatusLog.class));
    }

    private Shop shop(Long id) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setUserId(3L);
        shop.setStatus("ACTIVE");
        return shop;
    }
}
