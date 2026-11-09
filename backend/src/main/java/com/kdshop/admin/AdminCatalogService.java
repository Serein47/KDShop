package com.kdshop.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.admin.dto.AdminCategoryView;
import com.kdshop.admin.dto.AdminProductView;
import com.kdshop.admin.dto.CategoryRequest;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 平台治理：类目维护、商品强制上下架。 */
@Service
public class AdminCatalogService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final SkuMapper skuMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    @Autowired(required = false)
    private AuditService auditService;

    public AdminCatalogService(CategoryMapper categoryMapper, ProductMapper productMapper, ShopMapper shopMapper,
                               SkuMapper skuMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.shopMapper = shopMapper;
        this.skuMapper = skuMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public List<AdminCategoryView> categories() {
        requireAdmin();
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getParentId)
                .orderByAsc(Category::getSort));
        Map<Long, Long> counts = productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .isNull(Product::getDeletedAt)).stream()
                .collect(Collectors.groupingBy(Product::getCategoryId, Collectors.counting()));
        return all.stream()
                .map(c -> new AdminCategoryView(c.getId(), c.getParentId(), c.getName(), c.getSort(),
                        c.getStatus(), c.getFeatured() != null && c.getFeatured() == 1,
                        counts.getOrDefault(c.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional
    public AdminCategoryView createCategory(CategoryRequest request) {
        requireAdmin();
        if (request.parentId() != null && categoryMapper.selectById(request.parentId()) == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "上级类目不存在");
        }
        Category category = new Category();
        category.setParentId(request.parentId());
        category.setName(request.name().trim());
        category.setSort(request.sort() == null ? 100 : request.sort());
        category.setFeatured(Boolean.TRUE.equals(request.featured()) ? 1 : 0);
        category.setStatus("ON");
        categoryMapper.insert(category);
        return new AdminCategoryView(category.getId(), category.getParentId(), category.getName(),
                category.getSort(), category.getStatus(), category.getFeatured() == 1, 0);
    }

    @Transactional
    public AdminCategoryView updateCategory(Long id, CategoryRequest request) {
        requireAdmin();
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "类目不存在");
        }
        category.setName(request.name().trim());
        if (request.sort() != null) {
            category.setSort(request.sort());
        }
        if (request.featured() != null) {
            category.setFeatured(Boolean.TRUE.equals(request.featured()) ? 1 : 0);
        }
        if (StringUtils.hasText(request.status())) {
            category.setStatus(request.status().toUpperCase());
        }
        categoryMapper.updateById(category);
        return new AdminCategoryView(category.getId(), category.getParentId(), category.getName(),
                category.getSort(), category.getStatus(), category.getFeatured() == 1, 0);
    }

    public List<AdminProductView> products(Long merchantId, String status, String keyword) {
        requireAdmin();
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<Product>()
                .isNull(Product::getDeletedAt)
                .orderByDesc(Product::getId)
                .last("LIMIT 100");
        if (merchantId != null) {
            query.eq(Product::getMerchantId, merchantId);
        }
        if (StringUtils.hasText(status)) {
            query.eq(Product::getStatus, status.toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            query.like(Product::getName, keyword);
        }
        List<Product> products = productMapper.selectList(query);
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, Shop> shops = shopMapper.selectBatchIds(
                        products.stream().map(Product::getMerchantId).distinct().toList()).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        Map<Long, List<Sku>> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .in(Sku::getProductId, products.stream().map(Product::getId).toList())).stream()
                .collect(Collectors.groupingBy(Sku::getProductId));
        Map<Long, Category> categories = categoryMapper.selectBatchIds(
                        products.stream().map(Product::getCategoryId).distinct().toList()).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        return products.stream().map(p -> {
            List<Sku> list = skus.getOrDefault(p.getId(), List.of());
            int priceMin = list.stream().mapToInt(Sku::getPriceCent).min().orElse(0);
            int stock = list.stream().mapToInt(Sku::getStock).sum();
            Shop shop = shops.get(p.getMerchantId());
            Category category = categories.get(p.getCategoryId());
            return new AdminProductView(p.getId(), p.getName(), p.getMerchantId(),
                    shop == null ? null : shop.getShopName(), p.getCategoryId(),
                    category == null ? null : category.getName(), p.getStatus(), p.getOfflineReason(),
                    p.getMainImage(), priceMin, stock, p.getSalesCount() == null ? 0 : p.getSalesCount());
        }).toList();
    }

    @Transactional
    public void forceOffline(Long productId, String reason) {
        requireAdmin();
        Product product = requireProduct(productId);
        if ("OFF".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "商品已是下架状态");
        }
        product.setStatus("OFF");
        product.setOfflineReason(reason);
        productMapper.updateById(product);
        if (auditService != null) {
            auditService.record("ADMIN", currentUserId(), "PRODUCT_FORCE_OFFLINE", "PRODUCT",
                    String.valueOf(productId), Map.of("status", "ON"), Map.of("status", "OFF", "reason", reason));
        }
    }

    @Transactional
    public void forceOnline(Long productId) {
        requireAdmin();
        Product product = requireProduct(productId);
        product.setStatus("ON");
        product.setOfflineReason(null);
        productMapper.updateById(product);
        if (auditService != null) {
            auditService.record("ADMIN", currentUserId(), "PRODUCT_FORCE_ONLINE", "PRODUCT",
                    String.valueOf(productId), Map.of("status", "OFF"), Map.of("status", "ON"));
        }
    }

    private Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    private void requireAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "ADMIN"));
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId()));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
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
