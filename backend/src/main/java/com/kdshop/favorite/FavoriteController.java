package com.kdshop.favorite;

import com.kdshop.common.ApiResponse;
import com.kdshop.favorite.dto.FavoriteProductView;
import com.kdshop.favorite.dto.FollowedShopView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/favorites")
    public ApiResponse<List<FavoriteProductView>> favorites() {
        return ApiResponse.ok(favoriteService.listProducts());
    }

    @PostMapping("/favorites/{productId}")
    public ApiResponse<Map<String, Boolean>> favorite(@PathVariable Long productId) {
        favoriteService.add(productId);
        return ApiResponse.ok(Map.of("favorited", true));
    }

    @DeleteMapping("/favorites/{productId}")
    public ApiResponse<Map<String, Boolean>> unfavorite(@PathVariable Long productId) {
        favoriteService.remove(productId);
        return ApiResponse.ok(Map.of("favorited", false));
    }

    @GetMapping("/follows")
    public ApiResponse<List<FollowedShopView>> follows() {
        return ApiResponse.ok(favoriteService.listShops());
    }

    @PostMapping("/follows/{merchantId}")
    public ApiResponse<Map<String, Boolean>> follow(@PathVariable Long merchantId) {
        favoriteService.follow(merchantId);
        return ApiResponse.ok(Map.of("following", true));
    }

    @DeleteMapping("/follows/{merchantId}")
    public ApiResponse<Map<String, Boolean>> unfollow(@PathVariable Long merchantId) {
        favoriteService.unfollow(merchantId);
        return ApiResponse.ok(Map.of("following", false));
    }
}
