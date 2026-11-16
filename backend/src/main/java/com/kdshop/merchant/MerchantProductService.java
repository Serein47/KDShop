package com.kdshop.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.catalog.entity.Category;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.ProductOption;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.entity.Sku;
import com.kdshop.catalog.mapper.CategoryMapper;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ProductOptionMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.merchant.dto.MerchantProductView;
import com.kdshop.merchant.dto.ProductOptionRequest;
import com.kdshop.merchant.dto.ProductOptionView;
import com.kdshop.merchant.dto.ProductUpsertRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 商家商品管理：只操作本店商品，价格与库存落在默认 SKU 上。 */
@Service
public class MerchantProductService {

    private static final Set<String> SALE_MODES = Set.of("IN_STOCK", "PREORDER", "CUSTOM");
    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final ProductOptionMapper productOptionMapper;
    private final SkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    public MerchantProductService(ShopMapper shopMapper, ProductMapper productMapper,
                                  ProductOptionMapper productOptionMapper, SkuMapper skuMapper,
                                  CategoryMapper categoryMapper, ObjectMapper objectMapper) {
        this.shopMapper = shopMapper;
        this.productMapper = productMapper;
        this.productOptionMapper = productOptionMapper;
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
        applySaleMode(product, request);
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
        applySaleMode(product, request);
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

    public List<ProductOptionView> options(Long productId) {
        Shop shop = currentShop();
        requireOwned(productId, shop.getId());
        return productOptionMapper.selectList(new LambdaQueryWrapper<ProductOption>()
                        .eq(ProductOption::getProductId, productId)
                        .orderByAsc(ProductOption::getSort)
                        .orderByAsc(ProductOption::getId))
                .stream().map(this::toOptionView).toList();
    }

    /** 全量替换定制项；已被历史订单快照引用的旧定义只做下线，新订单只认当前定义。 */
    @Transactional
    public List<ProductOptionView> replaceOptions(Long productId, List<ProductOptionRequest> requests) {
        Shop shop = currentShop();
        requireOwned(productId, shop.getId());
        List<ProductOptionRequest> safe = requests == null ? List.of() : requests;
        if (safe.size() > 10) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "一个商品最多 10 个定制项");
        }
        Set<String> names = new HashSet<>();
        for (int i = 0; i < safe.size(); i++) {
            ProductOptionRequest request = safe.get(i);
            if (!names.add(request.name().trim())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "定制项名称不能重复");
            }
            validateOption(request, i);
        }

        productOptionMapper.delete(new LambdaQueryWrapper<ProductOption>()
                .eq(ProductOption::getProductId, productId));
        List<ProductOptionView> result = new ArrayList<>();
        for (int i = 0; i < safe.size(); i++) {
            ProductOptionRequest request = safe.get(i);
            ProductOption option = new ProductOption();
            option.setProductId(productId);
            option.setName(request.name().trim());
            option.setType(request.type().toUpperCase());
            option.setRequired(Boolean.TRUE.equals(request.required()) ? 1 : 0);
            option.setMaxLength("TEXT".equalsIgnoreCase(request.type())
                    ? (request.maxLength() == null ? 20 : request.maxLength()) : null);
            option.setChoicesJson("ENUM".equalsIgnoreCase(request.type())
                    ? writeChoices(request.choices()) : null);
            option.setPriceDeltaCent(request.priceDelta() == null ? 0 : request.priceDelta());
            option.setSort(request.sort() == null ? i : request.sort());
            option.setStatus("ON");
            productOptionMapper.insert(option);
            result.add(toOptionView(option));
        }
        return result;
    }

    private void validateOption(ProductOptionRequest request, int index) {
        if ("TEXT".equalsIgnoreCase(request.type())) {
            int max = request.maxLength() == null ? 20 : request.maxLength();
            if (max < 1 || max > 50) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "第 " + (index + 1) + " 个定制项字数上限需在 1–50");
            }
            return;
        }
        if ("ENUM".equalsIgnoreCase(request.type())) {
            List<String> choices = request.choices() == null ? List.of() : request.choices().stream()
                    .filter(StringUtils::hasText).map(String::trim).distinct().toList();
            if (choices.size() < 2 || choices.size() > 10) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "第 " + (index + 1) + " 个单选项需要 2–10 个可选值");
            }
        }
    }

    private ProductOptionView toOptionView(ProductOption option) {
        return new ProductOptionView(option.getId(), option.getName(), option.getType(),
                option.getRequired() != null && option.getRequired() == 1,
                option.getMaxLength(), readList(option.getChoicesJson()),
                option.getPriceDeltaCent() == null ? 0 : option.getPriceDeltaCent(),
                option.getSort() == null ? 0 : option.getSort());
    }

    private String writeChoices(List<String> choices) {
        try {
            return objectMapper.writeValueAsString(choices == null ? List.of() : choices);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "可选值格式不合法");
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

    /**
     * 销售模式（ISSUE-33）：现货清空工期与定金；预售/定制强制 3–60 天、定金 1%–100%。
     * SKU 不存销售模式，全部继承商品，所以这里只落商品。
     */
    private void applySaleMode(Product product, ProductUpsertRequest request) {
        String mode = StringUtils.hasText(request.saleMode())
                ? request.saleMode().trim().toUpperCase() : "IN_STOCK";
        if (!SALE_MODES.contains(mode)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "销售模式只能是现货、预售或定制");
        }
        if ("IN_STOCK".equals(mode)) {
            product.setSaleMode("IN_STOCK");
            product.setLeadDays(null);
            product.setDepositRate(0);
            return;
        }
        Integer leadDays = request.leadDays();
        if (leadDays == null || leadDays < 3 || leadDays > 60) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "预售/定制商品需填写 3–60 天工期");
        }
        Integer rate = request.depositRate();
        if (rate == null || rate < 1 || rate > 10000) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "定金比例需在 1%–100% 之间");
        }
        product.setSaleMode(mode);
        product.setLeadDays(leadDays);
        product.setDepositRate(rate);
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
                product.getSalesCount() == null ? 0 : product.getSalesCount(),
                product.getSaleMode(), product.getLeadDays(), product.getDepositRate(), views);
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
