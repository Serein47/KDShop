package com.kdshop.admin;

import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userMapper, roleMapper, userRoleMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(8L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cannotDisableSelf() {
        when(roleMapper.selectOne(any())).thenReturn(adminRole());
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.setStatus(8L, false, "x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void disablingUserFlipsStatus() {
        when(roleMapper.selectOne(any())).thenReturn(adminRole());
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
        User user = new User();
        user.setId(2L);
        user.setStatus("ACTIVE");
        when(userMapper.selectById(2L)).thenReturn(user);
        when(roleMapper.selectList(any())).thenReturn(List.of(adminRole()));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        var view = service.setStatus(2L, false, "违规");

        assertThat(view.status()).isEqualTo("DISABLED");
        verify(userMapper).updateById(user);
    }

    @Test
    void assigningUnknownRoleIsRejected() {
        when(roleMapper.selectOne(any())).thenReturn(adminRole());
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
        User user = new User();
        user.setId(2L);
        when(userMapper.selectById(2L)).thenReturn(user);
        Role customer = new Role();
        customer.setId(1L);
        customer.setCode("CUSTOMER");
        when(roleMapper.selectList(any())).thenReturn(List.of(customer));

        assertThatThrownBy(() -> service.assignRoles(2L, List.of("SUPERUSER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void nonAdminCannotList() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.list(null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    private Role adminRole() {
        Role role = new Role();
        role.setId(5L);
        role.setCode("ADMIN");
        return role;
    }
}
