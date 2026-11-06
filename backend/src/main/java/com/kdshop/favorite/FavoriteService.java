package com.kdshop.favorite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.entity.Sku;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.favorite.dto.FavoriteProductView;
import com.kdshop.favorite.dto.FollowedShopView;
import com.kdshop.favorite.entity.Favorite;
import com.kdshop.favorite.entity.ShopFollow;
import com.kdshop.favorite.mapper.FavoriteMapper;
import com.kdshop.favorite.mapper.ShopFollowMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 收藏商品与关注店铺：幂等写入，解除时物理删除。 */
@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ShopFollowMapper followMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final ShopMapper shopMapper;

    public FavoriteService(FavoriteMapper favoriteMapper, ShopFollowMapper followMapper,
                           ProductMapper productMapper, SkuMapper skuMapper, ShopMapper shopMapper) {
        this.favoriteMapper = favoriteMapper;
        this.followMapper = followMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.shopMapper = shopMapper;
    }

    public List<FavoriteProductView> listProducts() {
        Long userId = currentUserId();
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreatedAt)
                .last("LIMIT 200"));
        if (favorites.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> products = productMapper.selectBatchIds(
                        favorites.stream().map(Favorite::getProductId).toList()).stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.toMap(Product::getId, p -> p));
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Sku>> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .in(Sku::getProductId, products.keySet())).stream()
                .collect(Collectors.groupingBy(Sku::getProductId));
        Map<Long, Shop> shops = shopMapper.selectBatchIds(
                        products.values().stream().map(Product::getMerchantId).distinct().toList()).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));

        return favorites.stream()
                .map(f -> products.get(f.getProductId()))
                .filter(p -> p != null)
                .map(p -> {
                    List<Sku> list = skus.getOrDefault(p.getId(), List.of());
                    int priceMin = list.stream().mapToInt(Sku::getPriceCent).min().orElse(0);
                    Shop shop = shops.get(p.getMerchantId());
                    return new FavoriteProductView(p.getId(), p.getName(), p.getSubtitle(), p.getMainImage(),
                            priceMin, p.getSalesCount() == null ? 0 : p.getSalesCount(),
                            p.getMerchantId(), shop == null ? null : shop.getShopName());
                })
                .toList();
    }

    @Transactional
    public void add(Long productId) {
        Long userId = currentUserId();
        Product product = productMapper.selectById(productId);
        if (product == null || product.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        Long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        if (exists != null && exists > 0) {
            return;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favoriteMapper.insert(favorite);
    }

    public void remove(Long productId) {
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, currentUserId())
                .eq(Favorite::getProductId, productId));
    }

    public List<FollowedShopView> listShops() {
        Long userId = currentUserId();
        List<ShopFollow> follows = followMapper.selectList(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .orderByDesc(ShopFollow::getCreatedAt)
                .last("LIMIT 200"));
        if (follows.isEmpty()) {
            return List.of();
        }
        Map<Long, Shop> shops = shopMapper.selectBatchIds(
                        follows.stream().map(ShopFollow::getMerchantId).toList()).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        Map<Long, Long> counts = productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .in(Product::getMerchantId, shops.keySet())
                        .eq(Product::getStatus, "ON")).stream()
                .collect(Collectors.groupingBy(Product::getMerchantId, Collectors.counting()));
        return follows.stream()
                .map(f -> shops.get(f.getMerchantId()))
                .filter(s -> s != null)
                .map(s -> new FollowedShopView(s.getId(), s.getShopName(), s.getLogo(), s.getDescription(),
                        counts.getOrDefault(s.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional
    public void follow(Long merchantId) {
        Long userId = currentUserId();
        Shop shop = shopMapper.selectById(merchantId);
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.SHOP_NOT_FOUND);
        }
        Long exists = followMapper.selectCount(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .eq(ShopFollow::getMerchantId, merchantId));
        if (exists != null && exists > 0) {
            return;
        }
        ShopFollow follow = new ShopFollow();
        follow.setUserId(userId);
        follow.setMerchantId(merchantId);
        followMapper.insert(follow);
    }

    public void unfollow(Long merchantId) {
        followMapper.delete(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, currentUserId())
                .eq(ShopFollow::getMerchantId, merchantId));
    }

    public boolean isFollowing(Long merchantId) {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            return false;
        }
        Long count = followMapper.selectCount(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .eq(ShopFollow::getMerchantId, merchantId));
        return count != null && count > 0;
    }

    public boolean isFavorited(Long productId) {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            return false;
        }
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        return count != null && count > 0;
    }

    private Long currentUserId() {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private Long currentUserIdOrNull() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return null;
        }
        return userId;
    }
}
