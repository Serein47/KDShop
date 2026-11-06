package com.kdshop.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.merchant.dto.DashboardView;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.mapper.MerchantOrderMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 本店看板：在售商品、待发货、累计销售与近 7 天走势。 */
@Service
public class MerchantDashboardService {

    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final MerchantOrderMapper merchantOrderMapper;

    public MerchantDashboardService(ShopMapper shopMapper, ProductMapper productMapper,
                                    MerchantOrderMapper merchantOrderMapper) {
        this.shopMapper = shopMapper;
        this.productMapper = productMapper;
        this.merchantOrderMapper = merchantOrderMapper;
    }

    public DashboardView dashboard() {
        Shop shop = currentShop();

        Long onSale = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getMerchantId, shop.getId())
                .eq(Product::getStatus, "ON"));
        Long pending = merchantOrderMapper.selectCount(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getMerchantId, shop.getId())
                .eq(MerchantOrder::getStatus, "PENDING_SHIPMENT"));
        Long shipped = merchantOrderMapper.selectCount(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getMerchantId, shop.getId())
                .eq(MerchantOrder::getStatus, "SHIPPED"));

        List<MerchantOrder> all = merchantOrderMapper.selectList(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getMerchantId, shop.getId()));
        int totalSales = all.stream()
                .filter(sub -> !"CANCELLED".equals(sub.getStatus()))
                .mapToInt(MerchantOrder::getPayAmountCent)
                .sum();

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);
        Map<String, int[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            buckets.put(from.plusDays(i).toString(), new int[]{0, 0});
        }
        for (MerchantOrder sub : all) {
            if ("CANCELLED".equals(sub.getStatus()) || sub.getCreatedAt() == null) {
                continue;
            }
            String day = sub.getCreatedAt().toLocalDate().toString();
            int[] bucket = buckets.get(day);
            if (bucket != null) {
                bucket[0] += 1;
                bucket[1] += sub.getPayAmountCent();
            }
        }
        List<DashboardView.TrendPoint> trend = buckets.entrySet().stream()
                .map(entry -> new DashboardView.TrendPoint(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .collect(Collectors.toList());

        int[] todayBucket = buckets.getOrDefault(today.toString(), new int[]{0, 0});

        int finalTotalSales = totalSales;
        return new DashboardView(shop.getShopName(), shop.getStatus(),
                onSale == null ? 0 : onSale.intValue(),
                pending == null ? 0 : pending.intValue(),
                shipped == null ? 0 : shipped.intValue(),
                finalTotalSales, todayBucket[0], todayBucket[1], trend);
    }

    private Shop currentShop() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, userId));
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_APPROVED, "店铺未开通");
        }
        return shop;
    }
}
