package com.kdshop.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.cart.dto.MergeRequest;
import com.kdshop.cart.dto.MergeResult;
import com.kdshop.cart.entity.CartItem;
import com.kdshop.cart.mapper.CartItemMapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.entity.Sku;
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
class CartServiceTest {

    @Mock private CartItemMapper cartItemMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ShopMapper shopMapper;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartItemMapper, skuMapper, productMapper, shopMapper, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addingBeyondStockIsRejectedWithAvailableCount() {
        when(skuMapper.selectById(10L)).thenReturn(sku(10L, 100L, 3));
        when(productMapper.selectById(100L)).thenReturn(product(100L, 1L));
        when(shopMapper.selectById(1L)).thenReturn(shop());
        when(cartItemMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> cartService.add(10L, 5))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);

        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    void addingSameSkuAccumulatesQuantity() {
        when(skuMapper.selectById(10L)).thenReturn(sku(10L, 100L, 10));
        when(productMapper.selectById(100L)).thenReturn(product(100L, 1L));
        when(shopMapper.selectById(1L)).thenReturn(shop());
        CartItem existing = new CartItem();
        existing.setId(7L);
        existing.setUserId(1L);
        existing.setSkuId(10L);
        existing.setQuantity(2);
        existing.setSelected(1);
        when(cartItemMapper.selectOne(any())).thenReturn(existing);

        var view = cartService.add(10L, 3);

        assertThat(view.quantity()).isEqualTo(5);
        verify(cartItemMapper).updateById(existing);
    }

    @Test
    void settingQuantityToZeroDeletesRow() {
        CartItem item = new CartItem();
        item.setId(7L);
        item.setUserId(1L);
        item.setSkuId(10L);
        when(cartItemMapper.selectById(7L)).thenReturn(item);

        cartService.update(7L, 0, null);

        verify(cartItemMapper).deleteById(7L);
    }

    @Test
    void touchingOthersCartIsForbidden() {
        CartItem item = new CartItem();
        item.setId(7L);
        item.setUserId(99L);
        when(cartItemMapper.selectById(7L)).thenReturn(item);

        assertThatThrownBy(() -> cartService.remove(7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    @Test
    void mergeDropsUnavailableSku() {
        when(skuMapper.selectById(10L)).thenReturn(sku(10L, 100L, 10));
        when(productMapper.selectById(100L)).thenReturn(product(100L, 1L));
        when(shopMapper.selectById(1L)).thenReturn(shop());
        when(cartItemMapper.selectOne(any())).thenReturn(null);
        when(skuMapper.selectById(11L)).thenReturn(sku(11L, 101L, 0));

        MergeResult result = cartService.merge(new MergeRequest(List.of(
                new MergeRequest.Item(10L, 1),
                new MergeRequest.Item(11L, 2))));

        assertThat(result.merged()).isEqualTo(1);
        assertThat(result.dropped()).hasSize(1);
        assertThat(result.dropped().get(0).skuId()).isEqualTo(11L);
    }

    private Sku sku(Long id, Long productId, int stock) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setProductId(productId);
        sku.setPriceCent(1000);
        sku.setStock(stock);
        sku.setLockedStock(0);
        sku.setStatus("ON");
        sku.setSpecJson("{}");
        return sku;
    }

    private Product product(Long id, Long merchantId) {
        Product product = new Product();
        product.setId(id);
        product.setMerchantId(merchantId);
        product.setCategoryId(1L);
        product.setStatus("ON");
        product.setName("测试商品");
        return product;
    }

    private Shop shop() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setStatus("ACTIVE");
        shop.setShopName("测试店铺");
        return shop;
    }
}
