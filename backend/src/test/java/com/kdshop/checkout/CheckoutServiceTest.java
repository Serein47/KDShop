package com.kdshop.checkout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.address.mapper.AddressMapper;
import com.kdshop.cart.mapper.CartItemMapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.entity.Sku;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.checkout.dto.CheckoutPreview;
import com.kdshop.checkout.dto.CheckoutRequest;
import com.kdshop.checkout.mapper.CouponMapper;
import com.kdshop.checkout.mapper.UserCouponMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.MetaProperties;
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
class CheckoutServiceTest {

    @Mock private CartItemMapper cartItemMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ShopMapper shopMapper;
    @Mock private AddressMapper addressMapper;
    @Mock private CouponMapper couponMapper;
    @Mock private UserCouponMapper userCouponMapper;

    private CheckoutService service;

    private final MetaProperties meta = new MetaProperties("CNY", "Asia/Shanghai", 9900, 1000, 15, 15, 7);

    @BeforeEach
    void setUp() {
        service = new CheckoutService(cartItemMapper, skuMapper, productMapper, shopMapper,
                addressMapper, couponMapper, userCouponMapper, meta, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shippingIsChargedPerMerchantBelowThreshold() {
        when(skuMapper.selectById(10L)).thenReturn(sku(10L, 100L, 5000, 10));
        when(productMapper.selectById(100L)).thenReturn(product(100L, 1L, "小件"));
        when(shopMapper.selectById(1L)).thenReturn(shop(1L, "甲店"));

        CheckoutPreview preview = service.preview(new CheckoutRequest(null, null,
                List.of(new CheckoutRequest.Item(10L, 1))));

        assertThat(preview.subOrders()).hasSize(1);
        assertThat(preview.summary().goodsAmount()).isEqualTo(5000);
        assertThat(preview.subOrders().get(0).shippingFee()).isEqualTo(1000);
        assertThat(preview.summary().payAmount()).isEqualTo(6000);
    }

    @Test
    void freeShippingAtThreshold() {
        when(skuMapper.selectById(10L)).thenReturn(sku(10L, 100L, 9900, 10));
        when(productMapper.selectById(100L)).thenReturn(product(100L, 1L, "刚好包邮"));
        when(shopMapper.selectById(1L)).thenReturn(shop(1L, "甲店"));

        CheckoutPreview preview = service.preview(new CheckoutRequest(null, null,
                List.of(new CheckoutRequest.Item(10L, 1))));

        assertThat(preview.summary().shippingFee()).isZero();
        assertThat(preview.summary().payAmount()).isEqualTo(9900);
    }

    @Test
    void stockShortageIsRejectedBeforeCheckout() {
        when(skuMapper.selectById(10L)).thenReturn(sku(10L, 100L, 1000, 2));
        when(productMapper.selectById(100L)).thenReturn(product(100L, 1L, "紧俏"));
        when(shopMapper.selectById(1L)).thenReturn(shop(1L, "甲店"));

        assertThatThrownBy(() -> service.preview(new CheckoutRequest(null, null,
                List.of(new CheckoutRequest.Item(10L, 3)))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
    }

    @Test
    void ordersAreSplitByMerchant() {
        when(skuMapper.selectById(10L)).thenReturn(sku(10L, 100L, 5000, 10));
        when(skuMapper.selectById(11L)).thenReturn(sku(11L, 200L, 6000, 10));
        when(productMapper.selectById(100L)).thenReturn(product(100L, 1L, "甲商品"));
        when(productMapper.selectById(200L)).thenReturn(product(200L, 2L, "乙商品"));
        when(shopMapper.selectById(1L)).thenReturn(shop(1L, "甲店"));
        when(shopMapper.selectById(2L)).thenReturn(shop(2L, "乙店"));

        CheckoutPreview preview = service.preview(new CheckoutRequest(null, null,
                List.of(new CheckoutRequest.Item(10L, 1), new CheckoutRequest.Item(11L, 1))));

        assertThat(preview.subOrders()).hasSize(2);
        assertThat(preview.summary().goodsAmount()).isEqualTo(11000);
        assertThat(preview.summary().payAmount())
                .isEqualTo(preview.subOrders().stream().mapToInt(CheckoutPreview.SubOrder::payAmount).sum());
    }

    private Sku sku(Long id, Long productId, int price, int stock) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setProductId(productId);
        sku.setPriceCent(price);
        sku.setStock(stock);
        sku.setLockedStock(0);
        sku.setStatus("ON");
        sku.setSpecJson("{}");
        return sku;
    }

    private Product product(Long id, Long merchantId, String name) {
        Product product = new Product();
        product.setId(id);
        product.setMerchantId(merchantId);
        product.setCategoryId(1L);
        product.setName(name);
        product.setStatus("ON");
        return product;
    }

    private Shop shop(Long id, String name) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setShopName(name);
        shop.setStatus("ACTIVE");
        return shop;
    }
}
