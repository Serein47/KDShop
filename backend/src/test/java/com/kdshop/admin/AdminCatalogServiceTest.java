package com.kdshop.admin;

import com.kdshop.admin.dto.CategoryRequest;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.entity.Category;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.mapper.CategoryMapper;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.catalog.mapper.SkuMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCatalogServiceTest {

    @Mock private CategoryMapper categoryMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ShopMapper shopMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;

    private AdminCatalogService service;

    @BeforeEach
    void setUp() {
        service = new AdminCatalogService(categoryMapper, productMapper, shopMapper, skuMapper,
                roleMapper, userRoleMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(8L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forceOfflineSetsReasonAndStatus() {
        when(roleMapper.selectOne(any())).thenReturn(role());
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
        Product product = new Product();
        product.setId(1L);
        product.setStatus("ON");
        when(productMapper.selectById(1L)).thenReturn(product);

        service.forceOffline(1L, "标题违规");

        assertThat(product.getStatus()).isEqualTo("OFF");
        assertThat(product.getOfflineReason()).isEqualTo("标题违规");
        verify(productMapper).updateById(product);
    }

    @Test
    void nonAdminCannotForceOffline() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.forceOffline(1L, "x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);

        verify(productMapper, never()).updateById(any(Product.class));
    }

    @Test
    void creatingChildCategoryWithMissingParentIsRejected() {
        when(roleMapper.selectOne(any())).thenReturn(role());
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
        when(categoryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.createCategory(new CategoryRequest(999L, "新类目", 1, false, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    private Role role() {
        Role role = new Role();
        role.setId(5L);
        role.setCode("ADMIN");
        return role;
    }
}
