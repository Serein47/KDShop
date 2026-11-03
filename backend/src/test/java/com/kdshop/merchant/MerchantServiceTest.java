package com.kdshop.merchant;

import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.merchant.dto.ApplyRequest;
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
class MerchantServiceTest {

    @Mock private ShopMapper shopMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleMapper roleMapper;

    private MerchantService service;

    @BeforeEach
    void setUp() {
        service = new MerchantService(shopMapper, userRoleMapper, roleMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(2L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void applyingCreatesPendingShop() {
        when(shopMapper.selectOne(any())).thenReturn(null);
        when(shopMapper.selectCount(any())).thenReturn(0L);

        var view = service.apply(new ApplyRequest("李四杂货铺", "李四", "13800009001", "测试", null));

        assertThat(view.status()).isEqualTo("PENDING");
        verify(shopMapper).insert(any(Shop.class));
    }

    @Test
    void applyingTwiceWhilePendingIsRejected() {
        Shop existing = new Shop();
        existing.setId(9L);
        existing.setUserId(2L);
        existing.setStatus("PENDING");
        when(shopMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.apply(new ApplyRequest("李四杂货铺", "李四", "13800009001", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MERCHANT_APPLY_DUPLICATE);

        verify(shopMapper, never()).insert(any(Shop.class));
    }

    @Test
    void approvingGrantsMerchantRole() {
        Shop pending = new Shop();
        pending.setId(9L);
        pending.setUserId(2L);
        pending.setStatus("PENDING");
        pending.setShopName("李四杂货铺");
        when(roleMapper.selectOne(any())).thenReturn(role(2L, "ADMIN"), role(2L, "MERCHANT"));
        when(userRoleMapper.selectCount(any())).thenReturn(1L, 0L);
        when(shopMapper.selectById(9L)).thenReturn(pending);

        var view = service.approve(9L);

        assertThat(view.status()).isEqualTo("ACTIVE");
        verify(userRoleMapper).insert(any(UserRole.class));
    }

    @Test
    void approvingWithoutAdminRoleIsForbidden() {
        when(roleMapper.selectOne(any())).thenReturn(role(2L, "ADMIN"));
        when(userRoleMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.approve(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        return role;
    }
}
