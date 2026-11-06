package com.kdshop.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.catalog.entity.Category;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.entity.Sku;
import com.kdshop.catalog.mapper.CategoryMapper;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.merchant.dto.MerchantProductView;
import com.kdshop.merchant.dto.ProductUpsertRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 商家商品管理：只操作本店商品，价格与库存落在默认 SKU 上。 */
@Service
public class MerchantProductService {

    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    public MerchantProductService(ShopMapper shopMapper, ProductMapper productMapper, SkuMapper skuMapper,
                                  CategoryMapper categoryMapper, ObjectMapper objectMapper) {
        this.shopMapper = shopMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
        this.objectMapper = objectMapper;
    }

    public List<MerchantProductView> list(String status) {
        Shop shop = currentShop();
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<Product>()
                .eq(Product::getMerchantId, shop.getId())
                .orderByDesc(Product::getId);
        if (StringUtils.hasText(status)) {
            query.eq(Product::getStatus, status.toUpperCase());
        }
        List<Product> products = productMapper.selectList(query);
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Sku>> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .in(Sku::getProductId, products.stream().map(Product::getId).toList()))
                .stream().collect(Collectors.groupingBy(Sku::getProductId));
        return products.stream().map(p -> toView(p, skus.getOrDefault(p.getId(), List.of()))).toList();
    }

    @Transactional
    public MerchantProductView create(ProductUpsertRequest request) {
        Shop shop = currentShop();
        Category category = requireLeafCategory(request.categoryId());

        Product product = new Product();
        product.setMerchantId(shop.getId());
        product.setCategoryId(category.getId());
        product.setName(request.name().trim());
        product.setSubtitle(request.subtitle());
        product.setDescription(request.description());
        product.setMainImage(request.mainImage());
        product.setStatus("ON");
        product.setSaleMode("IN_STOCK");
        product.setDepositRate(0);
        product.setRequiresFlawAck(0);
        product.setIsUnique(0);
        product.setSalesCount(0);
        productMapper.insert(product);

        Sku sku = new Sku();
        sku.setProductId(product.getId());
        sku.setSpecJson("{\"规格\":\"标准\"}");
        sku.setPriceCent(request.price());
        sku.setStock(request.stock());
        sku.setLockedStock(0);
        sku.setStatus("ON");
        skuMapper.insert(sku);

        return toView(product, List.of(sku));
    }

    @Transactional
    public MerchantProductView update(Long productId, ProductUpsertRequest request) {
        Shop shop = currentShop();
        Product product = requireOwned(productId, shop.getId());
        requireLeafCategory(request.categoryId());

        product.setName(request.name().trim());
        product.setSubtitle(request.subtitle());
        product.setDescription(request.description());
        product.setCategoryId(request.categoryId());
        if (request.mainImage() != null) {
            product.setMainImage(request.mainImage());
        }
        productMapper.updateById(product);

        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>().eq(Sku::getProductId, productId));
        if (skus.isEmpty()) {
            Sku sku = new Sku();
            sku.setProductId(productId);
            sku.setSpecJson("{\"规格\":\"标准\"}");
            sku.setPriceCent(request.price());
            sku.setStock(request.stock());
            sku.setLockedStock(0);
            sku.setStatus("ON");
            skuMapper.insert(sku);
            skus = List.of(sku);
        } else {
            Sku first = skus.get(0);
            first.setPriceCent(request.price());
            first.setStock(request.stock());
            skuMapper.updateById(first);
        }
        return toView(product, skus);
    }

    @Transactional
    public MerchantProductView setStatus(Long productId, String status) {
        Shop shop = currentShop();
        Product product = requireOwned(productId, shop.getId());
        product.setStatus(status);
        productMapper.updateById(product);
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>().eq(Sku::getProductId, productId));
        return toView(product, skus);
    }

    private Product requireOwned(Long productId, Long merchantId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!product.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.MERCHANT_SCOPE_FORBIDDEN);
        }
        return product;
    }

    private Category requireLeafCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "类目不存在");
        }
        return category;
    }

    private MerchantProductView toView(Product product, List<Sku> skus) {
        Category category = categoryMapper.selectById(product.getCategoryId());
        int priceMin = skus.stream().mapToInt(Sku::getPriceCent).min().orElse(0);
        int priceMax = skus.stream().mapToInt(Sku::getPriceCent).max().orElse(0);
        int stock = skus.stream().mapToInt(Sku::getStock).sum();
        List<MerchantProductView.Sku> views = skus.stream()
                .map(s -> new MerchantProductView.Sku(s.getId(), s.getPriceCent(), s.getStock(),
                        readSpec(s.getSpecJson())))
                .toList();
        return new MerchantProductView(product.getId(), product.getName(), product.getSubtitle(),
                product.getCategoryId(), category == null ? null : category.getName(), product.getStatus(),
                product.getMainImage(), priceMin, priceMax, stock,
                product.getSalesCount() == null ? 0 : product.getSalesCount(), views);
    }

    private String readSpec(String json) {
        if (!StringUtils.hasText(json)) {
            return "";
        }
        try {
            Map<String, String> map = objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
            return String.join(" / ", map.values());
        } catch (Exception e) {
            return "";
        }
    }

    private Shop currentShop() {
        Long userId = currentUserId();
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, userId));
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_APPROVED, "还没有开通店铺");
        }
        return shop;
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
