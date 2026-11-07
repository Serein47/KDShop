package com.kdshop.admin;

import com.kdshop.admin.mapper.AuditLogMapper;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.OrderMapper;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private ShopMapper shopMapper;
    @Mock private ProductMapper productMapper;
    @Mock private UserMapper userMapper;
    @Mock private RefundMapper refundMapper;
    @Mock private AuditLogMapper auditLogMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleMapper roleMapper;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(orderMapper, shopMapper, productMapper, userMapper, refundMapper,
                auditLogMapper, userRoleMapper, roleMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(8L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dashboardCountsOnlyPaidOrdersMinusRefunds() {
        Role admin = new Role();
        admin.setId(5L);
        admin.setCode("ADMIN");
        when(roleMapper.selectOne(any())).thenReturn(admin);
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        Order paid = new Order();
        paid.setStatus("SHIPPED");
        paid.setPayAmountCent(10000);
        paid.setPaidAt(LocalDateTime.now());
        paid.setCreatedAt(LocalDateTime.now());

        Order unpaid = new Order();
        unpaid.setStatus("PENDING_PAYMENT");
        unpaid.setPayAmountCent(5000);

        when(orderMapper.selectList(any())).thenReturn(List.of(paid, unpaid));
        Refund refund = new Refund();
        refund.setStatus("REFUNDED");
        refund.setAmountCent(2000);
        when(refundMapper.selectList(any())).thenReturn(List.of(refund));
        lenient().when(shopMapper.selectCount(any())).thenReturn(3L);
        lenient().when(productMapper.selectCount(any())).thenReturn(10L);
        lenient().when(userMapper.selectCount(any())).thenReturn(20L);

        var view = service.dashboard();

        assertThat(view.gmv()).isEqualTo(8000);
        assertThat(view.orderCount()).isEqualTo(2);
        assertThat(view.paidOrderCount()).isEqualTo(1);
        assertThat(view.trend()).hasSize(7);
    }

    @Test
    void auditLogsRequireAdminRole() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.auditLogs(null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }
}
