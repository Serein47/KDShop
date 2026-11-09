package com.kdshop.admin;

import com.kdshop.admin.dto.AdminCategoryView;
import com.kdshop.admin.dto.AdminProductView;
import com.kdshop.admin.dto.CategoryRequest;
import com.kdshop.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

    private final AdminCatalogService adminCatalogService;

    public AdminCatalogController(AdminCatalogService adminCatalogService) {
        this.adminCatalogService = adminCatalogService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<AdminCategoryView>> categories() {
        return ApiResponse.ok(adminCatalogService.categories());
    }

    @PostMapping("/categories")
    public ApiResponse<AdminCategoryView> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(adminCatalogService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<AdminCategoryView> updateCategory(@PathVariable Long id,
                                                         @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(adminCatalogService.updateCategory(id, request));
    }

    @GetMapping("/products")
    public ApiResponse<List<AdminProductView>> products(@RequestParam(required = false) Long merchantId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminCatalogService.products(merchantId, status, keyword));
    }

    @PostMapping("/products/{id}/offline")
    public ApiResponse<Map<String, Boolean>> offline(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? "平台强制下架" : body.getOrDefault("reason", "平台强制下架");
        adminCatalogService.forceOffline(id, reason);
        return ApiResponse.ok(Map.of("offline", true));
    }

    @PostMapping("/products/{id}/online")
    public ApiResponse<Map<String, Boolean>> online(@PathVariable Long id) {
        adminCatalogService.forceOnline(id);
        return ApiResponse.ok(Map.of("online", true));
    }
}
