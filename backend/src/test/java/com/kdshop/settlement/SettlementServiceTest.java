package com.kdshop.settlement;

import com.kdshop.auth.entity.Role;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.settlement.entity.Settlement;
import com.kdshop.settlement.entity.SettlementItem;
import com.kdshop.settlement.mapper.SettlementItemMapper;
import com.kdshop.settlement.mapper.SettlementMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock private SettlementMapper settlementMapper;
    @Mock private SettlementItemMapper itemMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private ShopMapper shopMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleMapper roleMapper;

    private SettlementService service;

    @BeforeEach
    void setUp() {
        service = new SettlementService(settlementMapper, itemMapper, merchantOrderMapper, shopMapper,
                userRoleMapper, roleMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(8L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generatingDeductsCommissionAndMarksOrdersSettled() {
        when(roleMapper.selectOne(any())).thenReturn(role("ADMIN"));
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        Shop shop = new Shop();
        shop.setId(1L);
        shop.setShopName("山野杂货");
        shop.setStatus("ACTIVE");
        shop.setCommissionRate(500);
        when(shopMapper.selectList(any())).thenReturn(List.of(shop));
        when(settlementMapper.selectCount(any())).thenReturn(0L);

        MerchantOrder sub = new MerchantOrder();
        sub.setId(9L);
        sub.setPayAmountCent(25600);
        sub.setRefundedAmountCent(0);
        sub.setStatus("COMPLETED");
        sub.setCompletedAt(LocalDateTime.now().minusDays(1));
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));

        var views = service.generate(LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(views).hasSize(1);
        assertThat(views.get(0).grossAmount()).isEqualTo(25600);
        assertThat(views.get(0).commission()).isEqualTo(1280);
        assertThat(views.get(0).netAmount()).isEqualTo(24320);
        verify(settlementMapper).insert(any(Settlement.class));
        verify(itemMapper).insert(any(SettlementItem.class));
        verify(merchantOrderMapper).updateById(any(MerchantOrder.class));
    }

    @Test
    void settlementRequiresAdmin() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.listAll())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    private Role role(String code) {
        Role role = new Role();
        role.setId(5L);
        role.setCode(code);
        return role;
    }
}
