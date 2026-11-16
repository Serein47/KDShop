package com.kdshop.checkout;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.address.entity.Address;
import com.kdshop.address.mapper.AddressMapper;
import com.kdshop.cart.entity.CartItem;
import com.kdshop.cart.mapper.CartItemMapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.ProductOption;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.entity.Sku;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ProductOptionMapper;
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

    private record Row(Sku sku, Product product, Shop shop, int quantity,
                       List<CheckoutPreview.OptionSelection> options, int unitPrice) {
    }


    private final CartItemMapper cartItemMapper;
    private final SkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final ProductOptionMapper productOptionMapper;
    private final ShopMapper shopMapper;
    private final AddressMapper addressMapper;
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final MetaProperties meta;
    private final ObjectMapper objectMapper;

    public CheckoutService(CartItemMapper cartItemMapper, SkuMapper skuMapper, ProductMapper productMapper,
                           ProductOptionMapper productOptionMapper,
                           ShopMapper shopMapper, AddressMapper addressMapper, CouponMapper couponMapper,
                           UserCouponMapper userCouponMapper, MetaProperties meta, ObjectMapper objectMapper) {
        this.cartItemMapper = cartItemMapper;
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
        this.productOptionMapper = productOptionMapper;
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
            List<CheckoutPreview.OptionSelection> selections = resolveOptions(product, item.options());
            int optionDelta = selections.stream()
                    .mapToInt(CheckoutPreview.OptionSelection::priceDelta)
                    .sum();
            rows.add(new Row(sku, product, shop, item.quantity(), selections,
                    sku.getPriceCent() + optionDelta));
        }

        // 定金/尾款只按整单算，所以现货与预售/定制不能混在一张单里（ISSUE-34 定稿）
        boolean hasStock = rows.stream().anyMatch(row -> "IN_STOCK".equals(saleMode(row.product())));
        boolean hasPreorder = rows.stream().anyMatch(row -> !"IN_STOCK".equals(saleMode(row.product())));
        if (hasStock && hasPreorder) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "预售/定制商品请与现货分开下单");
        }

        Map<Long, List<Row>> byMerchant = rows.stream()
                .collect(Collectors.groupingBy(row -> row.product().getMerchantId(), LinkedHashMap::new, Collectors.toList()));

        int goodsTotal = rows.stream().mapToInt(row -> row.unitPrice() * row.quantity()).sum();

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
            int goods = groupRows.stream().mapToInt(row -> row.unitPrice() * row.quantity()).sum();
            int shipping = goods >= meta.shippingFreeThresholdCent() ? 0 : meta.shippingFeeCent();
            int discount = discounts.getOrDefault(entry.getKey(), 0);
            int pay = Math.max(goods - discount + shipping, 0);

            List<CheckoutPreview.Line> lines = groupRows.stream()
                    .map(row -> new CheckoutPreview.Line(
                            row.sku().getId(), row.product().getId(), row.product().getName(),
                            row.product().getMainImage(),
                            readMap(row.sku().getSpecJson()), row.unitPrice(),
                            row.quantity(), row.unitPrice() * row.quantity(),
                            row.product().getRequiresFlawAck() != null && row.product().getRequiresFlawAck() == 1,
                            row.product().getFlawNote(), row.options()))
                    .toList();

            Shop shop = groupRows.get(0).shop();
            subOrders.add(new CheckoutPreview.SubOrder(entry.getKey(), shop.getShopName(), lines,
                    goods, shipping, discount, pay));
            shippingTotal += shipping;
            payTotal += pay;
        }

        String orderType = "NORMAL";
        int deposit = 0;
        int balance = 0;
        if (hasPreorder) {
            orderType = rows.stream().anyMatch(row -> "PREORDER".equals(saleMode(row.product())))
                    ? "PREORDER" : "CUSTOM";
            deposit = deposit(rows, payTotal);
            balance = payTotal - deposit;
        }

        CheckoutPreview.Summary summary = new CheckoutPreview.Summary(
                goodsTotal, totalDiscount, shippingTotal, payTotal, orderType, deposit, balance);

        return new CheckoutPreview(subOrders, summary, coupons(userId, goodsTotal, request.couponId()),
                address(userId, request.addressId()));
    }

    private String saleMode(Product product) {
        return product.getSaleMode() == null ? "IN_STOCK" : product.getSaleMode();
    }

    /**
     * 定金 = Σ round(行金额 × 该商品定金比例)，整单最低 1 元，且不超过应付总额。
     * 尾款 = 应付 - 定金，两者之和恒等于订单应付。
     */
    private int deposit(List<Row> rows, int payTotal) {
        int raw = 0;
        for (Row row : rows) {
            int line = row.unitPrice() * row.quantity();
            int rate = row.product().getDepositRate() == null ? 0 : row.product().getDepositRate();
            raw += (int) Math.round(line * rate / 10000.0);
        }
        return Math.min(Math.max(raw, 100), payTotal);
    }

    /**
     * 校验并快照定制项：未知选项、必填缺失、文本超长、单选值非法都返回 10001。
     * 加价以服务端定义为准，不信任前端价格。
     */
    private List<CheckoutPreview.OptionSelection> resolveOptions(Product product,
                                                                 List<CheckoutRequest.OptionValue> selected) {
        List<ProductOption> definitions = productOptionMapper.selectList(
                new LambdaQueryWrapper<ProductOption>()
                        .eq(ProductOption::getProductId, product.getId())
                        .eq(ProductOption::getStatus, "ON")
                        .orderByAsc(ProductOption::getSort));
        if (definitions.isEmpty()) {
            if (selected != null && !selected.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "该商品没有可选项");
            }
            return List.of();
        }
        Map<Long, String> values = new LinkedHashMap<>();
        if (selected != null) {
            for (CheckoutRequest.OptionValue value : selected) {
                values.put(value.optionId(), value.value());
            }
        }
        Map<Long, ProductOption> byId = definitions.stream()
                .collect(Collectors.toMap(ProductOption::getId, option -> option));
        for (Long optionId : values.keySet()) {
            if (!byId.containsKey(optionId)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "定制项不存在或已下架");
            }
        }

        List<CheckoutPreview.OptionSelection> result = new ArrayList<>();
        for (ProductOption definition : definitions) {
            String value = values.get(definition.getId());
            if (!StringUtils.hasText(value)) {
                if (definition.getRequired() != null && definition.getRequired() == 1) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID,
                            "请填写或选择：" + definition.getName());
                }
                continue;
            }
            String trimmed = value.trim();
            if ("TEXT".equals(definition.getType())) {
                int max = definition.getMaxLength() == null ? 50 : definition.getMaxLength();
                if (trimmed.length() > max) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID,
                            definition.getName() + "最多 " + max + " 字");
                }
            } else if ("ENUM".equals(definition.getType())
                    && !readList(definition.getChoicesJson()).contains(trimmed)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        definition.getName() + "的取值不合法");
            }
            result.add(new CheckoutPreview.OptionSelection(definition.getId(), definition.getName(),
                    trimmed, definition.getPriceDeltaCent() == null ? 0 : definition.getPriceDeltaCent()));
        }
        return result;
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
        return cart.stream().map(item -> new CheckoutRequest.Item(item.getSkuId(), item.getQuantity(), null)).toList();
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

    private List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
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
