package com.kdshop.cart;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.cart.dto.CartGroup;
import com.kdshop.cart.dto.CartItemView;
import com.kdshop.cart.dto.CartView;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final SkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final ObjectMapper objectMapper;

    public CartService(CartItemMapper cartItemMapper, SkuMapper skuMapper, ProductMapper productMapper,
                       ShopMapper shopMapper, ObjectMapper objectMapper) {
        this.cartItemMapper = cartItemMapper;
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
        this.shopMapper = shopMapper;
        this.objectMapper = objectMapper;
    }

    public CartView view() {
        Long userId = currentUserId();
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getUpdatedAt));
        if (items.isEmpty()) {
            return new CartView(List.of(), new CartView.Summary(0, 0));
        }

        Map<Long, Sku> skus = skuMapper.selectBatchIds(
                        items.stream().map(CartItem::getSkuId).toList()).stream()
                .collect(Collectors.toMap(Sku::getId, s -> s));
        Map<Long, Product> products = productMapper.selectBatchIds(
                        skus.values().stream().map(Sku::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, Shop> shops = shopMapper.selectBatchIds(
                        products.values().stream().map(Product::getMerchantId).distinct().toList()).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));

        Map<Long, List<CartItemView>> byMerchant = new LinkedHashMap<>();
        int selectedCount = 0;
        int goodsAmount = 0;

        for (CartItem item : items) {
            Sku sku = skus.get(item.getSkuId());
            if (sku == null) {
                continue;
            }
            Product product = products.get(sku.getProductId());
            if (product == null) {
                continue;
            }
            int available = available(sku);
            int subtotal = sku.getPriceCent() * item.getQuantity();
            boolean selected = item.getSelected() != null && item.getSelected() == 1;
            if (selected) {
                selectedCount += item.getQuantity();
                goodsAmount += subtotal;
            }
            CartItemView view = new CartItemView(item.getId(), sku.getId(), product.getId(), product.getName(),
                    readMap(sku.getSpecJson()), product.getMainImage(), sku.getPriceCent(),
                    item.getQuantity(), available, selected, subtotal);
            byMerchant.computeIfAbsent(product.getMerchantId(), key -> new ArrayList<>()).add(view);
        }

        List<CartGroup> groups = new ArrayList<>();
        for (Map.Entry<Long, List<CartItemView>> entry : byMerchant.entrySet()) {
            Shop shop = shops.get(entry.getKey());
            int subtotal = entry.getValue().stream()
                    .filter(i -> Boolean.TRUE.equals(i.selected()))
                    .mapToInt(CartItemView::subtotal).sum();
            groups.add(new CartGroup(entry.getKey(), shop == null ? null : shop.getShopName(),
                    entry.getValue(), subtotal));
        }
        return new CartView(groups, new CartView.Summary(selectedCount, goodsAmount));
    }

    @Transactional
    public CartItemView add(Long skuId, int quantity) {
        Long userId = currentUserId();
        Sku sku = requireSku(skuId);
        int available = available(sku);

        CartItem existing = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, skuId));

        int target = (existing == null ? 0 : existing.getQuantity()) + quantity;
        if (target > available) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                    "库存不足，当前可买 " + available + " 件",
                    Map.of("skuId", skuId, "available", available));
        }

        if (existing == null) {
            existing = new CartItem();
            existing.setUserId(userId);
            existing.setSkuId(skuId);
            existing.setQuantity(quantity);
            existing.setSelected(1);
            cartItemMapper.insert(existing);
        } else {
            existing.setQuantity(target);
            cartItemMapper.updateById(existing);
        }
        return toView(existing, sku);
    }

    @Transactional
    public void update(Long itemId, Integer quantity, Boolean selected) {
        CartItem item = requireOwnedItem(itemId);
        if (quantity != null) {
            if (quantity == 0) {
                cartItemMapper.deleteById(itemId);
                return;
            }
            Sku sku = requireSku(item.getSkuId());
            if (quantity > available(sku)) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                        "库存不足，当前可买 " + available(sku) + " 件", Map.of("available", available(sku)));
            }
            item.setQuantity(quantity);
        }
        if (selected != null) {
            item.setSelected(selected ? 1 : 0);
        }
        cartItemMapper.updateById(item);
    }

    public void remove(Long itemId) {
        requireOwnedItem(itemId);
        cartItemMapper.deleteById(itemId);
    }

    @Transactional
    public MergeResult merge(MergeRequest request) {
        int merged = 0;
        List<MergeResult.Dropped> dropped = new ArrayList<>();
        for (MergeRequest.Item item : request.items()) {
            try {
                add(item.skuId(), item.quantity());
                merged++;
            } catch (BusinessException e) {
                Integer available = null;
                if (e.data() instanceof Map<?, ?> data && data.get("available") instanceof Integer value) {
                    available = value;
                }
                dropped.add(new MergeResult.Dropped(item.skuId(), e.errorCode().name(), available));
            }
        }
        return new MergeResult(merged, dropped);
    }

    private CartItemView toView(CartItem item, Sku sku) {
        Product product = productMapper.selectById(sku.getProductId());
        return new CartItemView(item.getId(), sku.getId(), sku.getProductId(),
                product == null ? null : product.getName(), readMap(sku.getSpecJson()),
                product == null ? null : product.getMainImage(), sku.getPriceCent(),
                item.getQuantity(), available(sku), item.getSelected() != null && item.getSelected() == 1,
                sku.getPriceCent() * item.getQuantity());
    }

    private Sku requireSku(Long skuId) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null || !"ON".equals(sku.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "规格不存在或已下架");
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || !"ON".equals(product.getStatus()) || product.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "商品已下架");
        }
        Shop shop = shopMapper.selectById(product.getMerchantId());
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "店铺不可用");
        }
        return sku;
    }

    private CartItem requireOwnedItem(Long itemId) {
        CartItem item = cartItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getUserId(), currentUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        return item;
    }

    private int available(Sku sku) {
        int stock = sku.getStock() == null ? 0 : sku.getStock();
        int locked = sku.getLockedStock() == null ? 0 : sku.getLockedStock();
        return Math.max(stock - locked, 0);
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
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
}
