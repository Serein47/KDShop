package com.kdshop.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import com.kdshop.refund.entity.Refund;
import com.kdshop.refund.mapper.RefundMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private RefundMapper refundMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private ShopMapper shopMapper;
    @Mock private OrderStatusLogMapper statusLogMapper;
    @Mock private EmailOutboxMapper emailOutboxMapper;
    @Mock private UserMapper userMapper;

    private RefundService service;

    @BeforeEach
    void setUp() {
        service = new RefundService(refundMapper, merchantOrderMapper, orderMapper, shopMapper,
                statusLogMapper, emailOutboxMapper, userMapper, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void applyingOnOthersOrderIsForbidden() {
        MerchantOrder sub = new MerchantOrder();
        sub.setId(9L);
        sub.setOrderId(5L);
        sub.setStatus("SHIPPED");
        when(merchantOrderMapper.selectOne(any())).thenReturn(sub);
        Order order = new Order();
        order.setId(5L);
        order.setUserId(99L);
        lenient().when(orderMapper.selectById(5L)).thenReturn(order);

        assertThatThrownBy(() -> service.apply("KD1-1", "不合适"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);

        verify(refundMapper, never()).insert(any(Refund.class));
    }

    @Test
    void approvingWritesMailAndUpdatesSubOrder() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setRefundNo("RF1");
        refund.setMerchantOrderId(9L);
        refund.setOrderId(5L);
        refund.setMerchantId(1L);
        refund.setAmountCent(12800);
        refund.setStatus("PENDING");
        when(refundMapper.selectOne(any())).thenReturn(refund);

        com.kdshop.catalog.entity.Shop shop = new com.kdshop.catalog.entity.Shop();
        shop.setId(1L);
        shop.setUserId(3L);
        shop.setStatus("ACTIVE");
        when(shopMapper.selectOne(any())).thenReturn(shop);

        MerchantOrder sub = new MerchantOrder();
        sub.setId(9L);
        sub.setOrderId(5L);
        sub.setPayAmountCent(12800);
        sub.setRefundedAmountCent(0);
        when(merchantOrderMapper.selectById(9L)).thenReturn(sub);

        Order order = new Order();
        order.setId(5L);
        order.setOrderNo("KD1");
        order.setUserId(1L);
        when(orderMapper.selectById(5L)).thenReturn(order);

        service.approve("RF1");

        verify(emailOutboxMapper).insert(any(EmailOutbox.class));
        verify(merchantOrderMapper).updateById(any(MerchantOrder.class));
    }
}
