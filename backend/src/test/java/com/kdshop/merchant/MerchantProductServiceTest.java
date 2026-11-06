package com.kdshop.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.catalog.entity.Category;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.CategoryMapper;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.merchant.dto.ProductUpsertRequest;
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
class MerchantProductServiceTest {

    @Mock private ShopMapper shopMapper;
    @Mock private ProductMapper productMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private CategoryMapper categoryMapper;

    private MerchantProductService service;

    @BeforeEach
    void setUp() {
        service = new MerchantProductService(shopMapper, productMapper, skuMapper, categoryMapper, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(3L, null, List.of()));
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setUserId(3L);
        shop.setStatus("ACTIVE");
        when(shopMapper.selectOne(any())).thenReturn(shop);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void creatingLocksProductToCurrentShop() {
        Category category = new Category();
        category.setId(11L);
        category.setName("餐具");
        when(categoryMapper.selectById(11L)).thenReturn(category);

        var view = service.create(new ProductUpsertRequest("测试杯", "演示", null, 11L, 1990, 20, null));

        assertThat(view.name()).isEqualTo("测试杯");
        assertThat(view.status()).isEqualTo("ON");
        verify(productMapper).insert(any(Product.class));
        verify(skuMapper).insert(any(com.kdshop.catalog.entity.Sku.class));
    }

    @Test
    void updatingAnotherShopsProductIsForbidden() {
        Product other = new Product();
        other.setId(50L);
        other.setMerchantId(2L);
        when(productMapper.selectById(50L)).thenReturn(other);

        assertThatThrownBy(() -> service.update(50L, new ProductUpsertRequest("改", null, null, 11L, 100, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MERCHANT_SCOPE_FORBIDDEN);

        verify(productMapper, never()).updateById(any(Product.class));
    }

    @Test
    void togglingStatusUpdatesProduct() {
        Product mine = new Product();
        mine.setId(60L);
        mine.setMerchantId(1L);
        mine.setStatus("ON");
        when(productMapper.selectById(60L)).thenReturn(mine);

        var view = service.setStatus(60L, "OFF");

        assertThat(view.status()).isEqualTo("OFF");
        verify(productMapper).updateById(mine);
    }
}
