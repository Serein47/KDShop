package com.kdshop.favorite;

import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.favorite.entity.Favorite;
import com.kdshop.favorite.entity.ShopFollow;
import com.kdshop.favorite.mapper.FavoriteMapper;
import com.kdshop.favorite.mapper.ShopFollowMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock private FavoriteMapper favoriteMapper;
    @Mock private ShopFollowMapper followMapper;
    @Mock private ProductMapper productMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private ShopMapper shopMapper;

    private FavoriteService service;

    @BeforeEach
    void setUp() {
        service = new FavoriteService(favoriteMapper, followMapper, productMapper, skuMapper, shopMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void favoritingTwiceDoesNotDuplicate() {
        Product product = new Product();
        product.setId(1L);
        when(productMapper.selectById(1L)).thenReturn(product);
        when(favoriteMapper.selectCount(any())).thenReturn(1L);

        service.add(1L);

        verify(favoriteMapper, never()).insert(any(Favorite.class));
    }

    @Test
    void favoritingMissingProductIsRejected() {
        when(productMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.add(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void followingPendingShopIsRejected() {
        Shop shop = new Shop();
        shop.setId(3L);
        shop.setStatus("PENDING");
        when(shopMapper.selectById(3L)).thenReturn(shop);

        assertThatThrownBy(() -> service.follow(3L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    void unfollowingDeletesRow() {
        service.unfollow(1L);
        verify(followMapper).delete(any());
        verify(followMapper, never()).insert(any(ShopFollow.class));
    }
}
