package com.kdshop.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private ShopMapper shopMapper;
    @Mock private UserMapper userMapper;
    @Mock private RefundMapper refundMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;

    private AdminOrderService service;

    @BeforeEach
    void setUp() {
        service = new AdminOrderService(orderMapper, merchantOrderMapper, shopMapper, userMapper,
                refundMapper, roleMapper, userRoleMapper, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(8L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonAdminCannotSearchOrders() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.list(null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    @Test
    void adminSearchReturnsOrdersAcrossUsers() {
        when(roleMapper.selectOne(any())).thenReturn(role());
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
        Order order = new Order();
        order.setOrderNo("KD202611030005");
        order.setUserId(1L);
        order.setStatus("REFUNDED");
        order.setPayAmountCent(9800);
        order.setMerchantCount(1);
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());

        var list = service.list("KD2026", null, 1, 20);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).orderNo()).isEqualTo("KD202611030005");
    }

    private Role role() {
        Role role = new Role();
        role.setId(5L);
        role.setCode("ADMIN");
        return role;
    }
}
