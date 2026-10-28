package com.kdshop.catalog;

import com.kdshop.catalog.dto.CategoryNode;
import com.kdshop.catalog.dto.PageResult;
import com.kdshop.catalog.dto.ProductCard;
import com.kdshop.catalog.dto.ProductDetailView;
import com.kdshop.catalog.dto.ShopPage;
import com.kdshop.catalog.dto.TagView;
import com.kdshop.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 公开的商品目录接口，见 docs/接口文档/02-顾客端.md。 */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryNode>> categories() {
        return ApiResponse.ok(catalogService.categories());
    }

    @GetMapping("/tags")
    public ApiResponse<Map<String, List<TagView>>> tags() {
        return ApiResponse.ok(catalogService.tagsGrouped());
    }

    @GetMapping("/products")
    public ApiResponse<PageResult<ProductCard>> products(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String craft,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) String occasion,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(defaultValue = "sales_desc") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(catalogService.list(keyword, categoryId, craft, material, occasion,
                minPrice, maxPrice, sort, page, size));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.detail(id));
    }

    @GetMapping("/shops/{merchantId}")
    public ApiResponse<ShopPage> shop(@PathVariable Long merchantId) {
        return ApiResponse.ok(catalogService.shop(merchantId));
    }

    @GetMapping("/shops/{merchantId}/products")
    public ApiResponse<PageResult<ProductCard>> shopProducts(@PathVariable Long merchantId,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(catalogService.shopProducts(merchantId, page, size));
    }
}
