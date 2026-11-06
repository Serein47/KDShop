package com.kdshop.merchant;

import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.mapper.MerchantOrderMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantDashboardServiceTest {

    @Mock private ShopMapper shopMapper;
    @Mock private ProductMapper productMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;

    private MerchantDashboardService service;

    @BeforeEach
    void setUp() {
        service = new MerchantDashboardService(shopMapper, productMapper, merchantOrderMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(3L, null, List.of()));
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setUserId(3L);
        shop.setShopName("山野杂货");
        shop.setStatus("ACTIVE");
        when(shopMapper.selectOne(any())).thenReturn(shop);
        when(productMapper.selectCount(any())).thenReturn(21L);
        when(merchantOrderMapper.selectCount(any())).thenReturn(2L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cancelledOrdersAreExcludedFromSales() {
        MerchantOrder paid = new MerchantOrder();
        paid.setStatus("SHIPPED");
        paid.setPayAmountCent(25600);
        paid.setCreatedAt(LocalDateTime.now().minusDays(1));

        MerchantOrder cancelled = new MerchantOrder();
        cancelled.setStatus("CANCELLED");
        cancelled.setPayAmountCent(9900);
        cancelled.setCreatedAt(LocalDateTime.now());
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(paid, cancelled));

        var dashboard = service.dashboard();

        assertThat(dashboard.totalSales()).isEqualTo(25600);
        assertThat(dashboard.onSaleProducts()).isEqualTo(21);
        assertThat(dashboard.trend()).hasSize(7);
    }
}
