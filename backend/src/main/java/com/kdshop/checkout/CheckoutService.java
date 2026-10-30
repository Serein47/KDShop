package com.kdshop.checkout;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.address.entity.Address;
import com.kdshop.address.mapper.AddressMapper;
import com.kdshop.cart.entity.CartItem;
import com.kdshop.cart.mapper.CartItemMapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.entity.Sku;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.checkout.dto.CheckoutPreview;
import com.kdshop.checkout.dto.CheckoutRequest;
import com.kdshop.checkout.entity.Coupon;
import com.kdshop.checkout.entity.UserCoupon;
import com.kdshop.checkout.mapper.CouponMapper;
import com.kdshop.checkout.mapper.UserCouponMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.MetaProperties;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 结算预览：只算钱与拆单，不落库、不锁库存。
 * 金额规则见 ADR-0005：每商家独立运费，优惠按商品金额比例分摊，尾差归金额最大的子单。
 */
@Service
public class CheckoutService {

    private record Row(Sku sku, Product product, Shop shop, int quantity) {
    }


    private final CartItemMapper cartItemMapper;
    private final SkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final AddressMapper addressMapper;
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final MetaProperties meta;
    private final ObjectMapper objectMapper;

    public CheckoutService(CartItemMapper cartItemMapper, SkuMapper skuMapper, ProductMapper productMapper,
                           ShopMapper shopMapper, AddressMapper addressMapper, CouponMapper couponMapper,
                           UserCouponMapper userCouponMapper, MetaProperties meta, ObjectMapper objectMapper) {
        this.cartItemMapper = cartItemMapper;
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
        this.shopMapper = shopMapper;
        this.addressMapper = addressMapper;
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.meta = meta;
        this.objectMapper = objectMapper;
    }

    public CheckoutPreview preview(CheckoutRequest request) {
        Long userId = currentUserId();
        List<CheckoutRequest.Item> items = resolveItems(userId, request);

        List<Row> rows = new ArrayList<>();
        for (CheckoutRequest.Item item : items) {
            Sku sku = skuMapper.selectById(item.skuId());
            if (sku == null || !"ON".equals(sku.getStatus())) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "规格不存在或已下架");
            }
            Product product = productMapper.selectById(sku.getProductId());
            Shop shop = product == null ? null : shopMapper.selectById(product.getMerchantId());
            if (product == null || !"ON".equals(product.getStatus()) || product.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "商品已下架");
            }
            if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "店铺不可用");
            }
            int available = Math.max(sku.getStock() - sku.getLockedStock(), 0);
            if (item.quantity() > available) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                        product.getName() + " 库存不足，当前可买 " + available + " 件",
                        Map.of("skuId", sku.getId(), "available", available));
            }
            rows.add(new Row(sku, product, shop, item.quantity()));
        }

        Map<Long, List<Row>> byMerchant = rows.stream()
                .collect(Collectors.groupingBy(row -> row.product().getMerchantId(), LinkedHashMap::new, Collectors.toList()));

        int goodsTotal = rows.stream().mapToInt(row -> row.sku().getPriceCent() * row.quantity()).sum();

        int totalDiscount = 0;
        if (request.couponId() != null) {
            totalDiscount = resolveDiscount(userId, request.couponId(), goodsTotal);
        }

        Map<Long, Integer> discounts = allocate(totalDiscount, byMerchant);

        List<CheckoutPreview.SubOrder> subOrders = new ArrayList<>();
        int shippingTotal = 0;
        int payTotal = 0;

        for (Map.Entry<Long, List<Row>> entry : byMerchant.entrySet()) {
            List<Row> groupRows = entry.getValue();
            int goods = groupRows.stream().mapToInt(row -> row.sku().getPriceCent() * row.quantity()).sum();
            int shipping = goods >= meta.shippingFreeThresholdCent() ? 0 : meta.shippingFeeCent();
            int discount = discounts.getOrDefault(entry.getKey(), 0);
            int pay = Math.max(goods - discount + shipping, 0);

            List<CheckoutPreview.Line> lines = groupRows.stream()
                    .map(row -> new CheckoutPreview.Line(
                            row.sku().getId(), row.product().getId(), row.product().getName(),
                            readMap(row.sku().getSpecJson()), row.sku().getPriceCent(),
                            row.quantity(), row.sku().getPriceCent() * row.quantity()))
                    .toList();

            Shop shop = groupRows.get(0).shop();
            subOrders.add(new CheckoutPreview.SubOrder(entry.getKey(), shop.getShopName(), lines,
                    goods, shipping, discount, pay));
            shippingTotal += shipping;
            payTotal += pay;
        }

        CheckoutPreview.Summary summary = new CheckoutPreview.Summary(
                goodsTotal, totalDiscount, shippingTotal, payTotal);

        return new CheckoutPreview(subOrders, summary, coupons(userId, goodsTotal, request.couponId()),
                address(userId, request.addressId()));
    }

    private List<CheckoutRequest.Item> resolveItems(Long userId, CheckoutRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            return request.items();
        }
        List<CartItem> cart = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, 1));
        if (cart.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }
        return cart.stream().map(item -> new CheckoutRequest.Item(item.getSkuId(), item.getQuantity())).toList();
    }

    private int resolveDiscount(Long userId, Long couponId, int goodsTotal) {
        UserCoupon userCoupon = userCouponMapper.selectOne(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId));
        if (userCoupon == null || !"UNUSED".equals(userCoupon.getStatus())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券不可用");
        }
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !"ON".equals(coupon.getStatus())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartsAt()) || now.isAfter(coupon.getEndsAt())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券已过期");
        }
        if (goodsTotal < coupon.getMinAmountCent()) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "未达到使用门槛");
        }
        return Math.min(coupon.getValueCent(), goodsTotal);
    }

    /** 按商品金额比例分摊，尾差给金额最大的子单。 */
    private Map<Long, Integer> allocate(int totalDiscount, Map<Long, List<Row>> byMerchant) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (totalDiscount <= 0) {
            return result;
        }
        int goodsTotal = byMerchant.values().stream()
                .flatMap(List::stream)
                .mapToInt(row -> row.sku().getPriceCent() * row.quantity()).sum();
        if (goodsTotal <= 0) {
            return result;
        }

        int allocated = 0;
        Long largestMerchant = null;
        int largestGoods = -1;
        for (Map.Entry<Long, List<Row>> entry : byMerchant.entrySet()) {
            int goods = entry.getValue().stream().mapToInt(row -> row.sku().getPriceCent() * row.quantity()).sum();
            int share = (int) ((long) totalDiscount * goods / goodsTotal);
            result.put(entry.getKey(), share);
            allocated += share;
            if (goods > largestGoods) {
                largestGoods = goods;
                largestMerchant = entry.getKey();
            }
        }
        if (largestMerchant != null && allocated < totalDiscount) {
            result.merge(largestMerchant, totalDiscount - allocated, Integer::sum);
        }
        return result;
    }

    private CheckoutPreview.Coupons coupons(Long userId, int goodsTotal, Long selectedCouponId) {
        List<UserCoupon> owned = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId));
        List<CheckoutPreview.CouponView> usable = new ArrayList<>();
        List<CheckoutPreview.Unusable> unusable = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (UserCoupon uc : owned) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon == null) {
                continue;
            }
            if (!"UNUSED".equals(uc.getStatus())) {
                unusable.add(new CheckoutPreview.Unusable(coupon.getId(), coupon.getName(), "ALREADY_USED"));
                continue;
            }
            if (!"ON".equals(coupon.getStatus()) || now.isAfter(coupon.getEndsAt())) {
                unusable.add(new CheckoutPreview.Unusable(coupon.getId(), coupon.getName(), "EXPIRED"));
                continue;
            }
            if (goodsTotal < coupon.getMinAmountCent()) {
                unusable.add(new CheckoutPreview.Unusable(coupon.getId(), coupon.getName(), "GAP_NOT_REACHED"));
                continue;
            }
            usable.add(new CheckoutPreview.CouponView(uc.getId(), coupon.getId(), coupon.getName(),
                    coupon.getType(), coupon.getValueCent(), coupon.getMinAmountCent(),
                    coupon.getEndsAt().toLocalDate().toString()));
        }
        return new CheckoutPreview.Coupons(usable, unusable);
    }

    private CheckoutPreview.AddressView address(Long userId, Long addressId) {
        Address address = addressId != null
                ? addressMapper.selectById(addressId)
                : addressMapper.selectOne(new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .isNull(Address::getDeletedAt)
                        .orderByDesc(Address::getIsDefault)
                        .last("LIMIT 1"));
        if (address == null || address.getDeletedAt() != null || !address.getUserId().equals(userId)) {
            return null;
        }
        return new CheckoutPreview.AddressView(address.getReceiver(), address.getPhone(),
                address.getProvince(), address.getCity(), address.getDistrict(), address.getDetail());
    }

    private Map<String, String> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
