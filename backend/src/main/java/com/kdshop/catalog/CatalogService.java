package com.kdshop.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.catalog.dto.CategoryNode;
import com.kdshop.catalog.dto.PageResult;
import com.kdshop.catalog.dto.ProductCard;
import com.kdshop.catalog.dto.ProductDetailView;
import com.kdshop.catalog.dto.ShopPage;
import com.kdshop.catalog.dto.TagView;
import com.kdshop.catalog.entity.Category;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.ProductOption;
import com.kdshop.catalog.entity.ProductTag;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.entity.Sku;
import com.kdshop.catalog.entity.Tag;
import com.kdshop.catalog.mapper.CategoryMapper;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ProductOptionMapper;
import com.kdshop.catalog.mapper.ProductTagMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.catalog.mapper.TagMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品目录读模型。
 * 规模假设：百级 SKU。列表在内存里排序与分页，超过千级商品时应改为 SQL 分页 + 覆盖索引。
 */
@Service
public class CatalogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ProductTagMapper productTagMapper;
    private final ProductOptionMapper productOptionMapper;
    private final ShopMapper shopMapper;
    private final ObjectMapper objectMapper;

    public CatalogService(ProductMapper productMapper, SkuMapper skuMapper, CategoryMapper categoryMapper,
                          TagMapper tagMapper, ProductTagMapper productTagMapper,
                          ProductOptionMapper productOptionMapper, ShopMapper shopMapper, ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.productTagMapper = productTagMapper;
        this.productOptionMapper = productOptionMapper;
        this.shopMapper = shopMapper;
        this.objectMapper = objectMapper;
    }

    public List<CategoryNode> categories() {
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, "ON")
                .orderByAsc(Category::getSort));
        Map<Long, List<Category>> byParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        return all.stream()
                .filter(c -> c.getParentId() == null)
                .map(c -> new CategoryNode(c.getId(), c.getName(), c.getSort(),
                        byParent.getOrDefault(c.getId(), List.of()).stream()
                                .map(child -> new CategoryNode(child.getId(), child.getName(), child.getSort(), List.of()))
                                .toList()))
                .toList();
    }

    public Map<String, List<TagView>> tagsGrouped() {
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getStatus, "ON")
                .orderByAsc(Tag::getDimension, Tag::getSort));
        Map<String, List<TagView>> grouped = new LinkedHashMap<>();
        grouped.put("CRAFT", new ArrayList<>());
        grouped.put("MATERIAL", new ArrayList<>());
        grouped.put("OCCASION", new ArrayList<>());
        for (Tag tag : tags) {
            grouped.computeIfAbsent(tag.getDimension(), key -> new ArrayList<>())
                    .add(new TagView(tag.getId(), tag.getName(), tag.getSlug()));
        }
        return grouped;
    }

    public PageResult<ProductCard> list(String keyword, Long categoryId, String craft, String material,
                                        String occasion, Integer minPrice, Integer maxPrice,
                                        String sort, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "ON")
                .isNull(Product::getDeletedAt)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Product::getName, keyword)
                        .or()
                        .like(Product::getSubtitle, keyword))
                .exists("SELECT 1 FROM merchants m WHERE m.id = products.merchant_id AND m.status = 'ACTIVE'");

        if (categoryId != null) {
            wrapper.in(Product::getCategoryId, categoryWithChildren(categoryId));
        }
        addRangeExists(wrapper, minPrice, maxPrice);
        addTagExists(wrapper, "CRAFT", craft);
        addTagExists(wrapper, "MATERIAL", material);
        addTagExists(wrapper, "OCCASION", occasion);

        List<Product> matched = productMapper.selectList(wrapper);
        Map<Long, List<Sku>> skusByProduct = loadSkus(matched.stream().map(Product::getId).toList());
        Map<Long, Shop> shops = loadShops(matched);

        List<ProductCard> cards = matched.stream()
                .filter(p -> !skusByProduct.getOrDefault(p.getId(), List.of()).isEmpty())
                .map(p -> toCard(p, skusByProduct.get(p.getId()), shops.get(p.getMerchantId())))
                .collect(Collectors.toCollection(ArrayList::new));

        Comparator<ProductCard> comparator = switch (sort == null ? "" : sort) {
            case "price_asc" -> Comparator.comparing(ProductCard::minPrice);
            case "price_desc" -> Comparator.comparing(ProductCard::minPrice).reversed();
            case "created_desc" -> Comparator.comparing(ProductCard::id).reversed();
            default -> Comparator.comparing(ProductCard::salesCount).reversed();
        };
        cards.sort(comparator);

        int from = Math.min((safePage - 1) * safeSize, cards.size());
        int to = Math.min(from + safeSize, cards.size());
        return new PageResult<>(cards.subList(from, to), cards.size(), safePage, safeSize, to < cards.size());
    }

    public ProductDetailView detail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || !"ON".equals(product.getStatus()) || product.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getProductId, id)
                .eq(Sku::getStatus, "ON")
                .orderByAsc(Sku::getPriceCent));
        Shop shop = shopMapper.selectById(product.getMerchantId());
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        List<ProductTag> links = productTagMapper.selectList(
                new LambdaQueryWrapper<ProductTag>().eq(ProductTag::getProductId, id));
        Map<Long, Tag> tagById = links.isEmpty() ? Map.of()
                : tagMapper.selectBatchIds(links.stream().map(ProductTag::getTagId).toList()).stream()
                        .collect(Collectors.toMap(Tag::getId, t -> t));
        Map<String, List<TagView>> tags = new LinkedHashMap<>();
        for (ProductTag link : links) {
            Tag tag = tagById.get(link.getTagId());
            if (tag == null) {
                continue;
            }
            tags.computeIfAbsent(tag.getDimension(), key -> new ArrayList<>())
                    .add(new TagView(tag.getId(), tag.getName(), tag.getSlug()));
        }

        List<ProductOption> options = productOptionMapper.selectList(new LambdaQueryWrapper<ProductOption>()
                .eq(ProductOption::getProductId, id)
                .eq(ProductOption::getStatus, "ON")
                .orderByAsc(ProductOption::getSort));

        return new ProductDetailView(
                product.getId(), product.getName(), product.getSubtitle(), product.getDescription(),
                readList(product.getImagesJson()),
                product.getCategoryId(),
                categoryMapper.selectById(product.getCategoryId()) == null
                        ? null : categoryMapper.selectById(product.getCategoryId()).getName(),
                new ProductDetailView.ShopView(shop.getId(), shop.getShopName(), shop.getLogo()),
                skus.stream().map(s -> new ProductDetailView.SkuView(
                        s.getId(), readMap(s.getSpecJson()), s.getPriceCent(), s.getOriginalPriceCent(), s.getStock())).toList(),
                tags,
                options.stream().map(o -> new ProductDetailView.OptionView(
                        o.getId(), o.getName(), o.getType(), o.getRequired() != null && o.getRequired() == 1,
                        o.getMaxLength(), readList(o.getChoicesJson()), o.getPriceDeltaCent())).toList(),
                product.getStory(), product.getEditionSize(), product.getEditionNo(),
                product.getIsUnique() != null && product.getIsUnique() == 1,
                product.getFlawNote(),
                product.getRequiresFlawAck() != null && product.getRequiresFlawAck() == 1,
                product.getSaleMode(), product.getLeadDays(), product.getDepositRate(),
                product.getSalesCount(), 0.0, 0L, false);
    }

    public ShopPage shop(Long merchantId) {
        Shop shop = shopMapper.selectById(merchantId);
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "店铺不存在或未通过审核");
        }
        Long count = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getMerchantId, merchantId)
                .eq(Product::getStatus, "ON")
                .isNull(Product::getDeletedAt));
        return new ShopPage(shop.getId(), shop.getShopName(), shop.getLogo(), shop.getDescription(),
                count == null ? 0 : count.intValue(), 0, shop.getCreatedAt());
    }

    public PageResult<ProductCard> shopProducts(Long merchantId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getMerchantId, merchantId)
                .eq(Product::getStatus, "ON")
                .isNull(Product::getDeletedAt)
                .orderByDesc(Product::getSalesCount));
        Map<Long, List<Sku>> skusByProduct = loadSkus(products.stream().map(Product::getId).toList());
        Shop shop = shopMapper.selectById(merchantId);
        List<ProductCard> cards = products.stream()
                .map(p -> toCard(p, skusByProduct.getOrDefault(p.getId(), List.of()), shop))
                .toList();
        int from = Math.min((safePage - 1) * safeSize, cards.size());
        int to = Math.min(from + safeSize, cards.size());
        return new PageResult<>(cards.subList(from, to), cards.size(), safePage, safeSize, to < cards.size());
    }

    private ProductCard toCard(Product p, List<Sku> skus, Shop shop) {
        int min = skus.stream().mapToInt(Sku::getPriceCent).min().orElse(0);
        int max = skus.stream().mapToInt(Sku::getPriceCent).max().orElse(0);
        return new ProductCard(p.getId(), p.getName(), p.getSubtitle(), p.getMainImage(),
                min, max, p.getSalesCount(), p.getMerchantId(),
                shop == null ? null : shop.getShopName(), p.getSaleMode(), p.getLeadDays(),
                p.getDepositRate(), p.getIsUnique() != null && p.getIsUnique() == 1,
                p.getEditionSize(), p.getEditionNo());
    }

    private List<Long> categoryWithChildren(Long categoryId) {
        List<Category> children = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, categoryId));
        if (children.isEmpty()) {
            return List.of(categoryId);
        }
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        children.forEach(child -> ids.add(child.getId()));
        return ids;
    }

    private void addRangeExists(LambdaQueryWrapper<Product> wrapper, Integer minPrice, Integer maxPrice) {
        if (minPrice == null && maxPrice == null) {
            return;
        }
        int min = minPrice == null ? 0 : minPrice;
        int max = maxPrice == null ? Integer.MAX_VALUE : maxPrice;
        wrapper.exists("SELECT 1 FROM skus s WHERE s.product_id = products.id AND s.status = 'ON' "
                + "AND s.price_cent >= {0} AND s.price_cent <= {1}", min, max);
    }

    private void addTagExists(LambdaQueryWrapper<Product> wrapper, String dimension, String slug) {
        if (!StringUtils.hasText(slug)) {
            return;
        }
        wrapper.exists("SELECT 1 FROM product_tags pt JOIN tags t ON t.id = pt.tag_id "
                + "WHERE pt.product_id = products.id AND t.dimension = {0} AND t.slug = {1}", dimension, slug);
    }

    private Map<Long, List<Sku>> loadSkus(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .in(Sku::getProductId, productIds)
                        .eq(Sku::getStatus, "ON"))
                .stream().collect(Collectors.groupingBy(Sku::getProductId));
    }

    private Map<Long, Shop> loadShops(List<Product> products) {
        List<Long> ids = products.stream().map(Product::getMerchantId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return shopMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Shop::getId, s -> s));
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
}
