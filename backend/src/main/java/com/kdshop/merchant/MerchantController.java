package com.kdshop.merchant;

import com.kdshop.common.ApiResponse;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.merchant.dto.ApplyRequest;
import com.kdshop.merchant.dto.MerchantView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping("/applications")
    public ApiResponse<MerchantView> apply(@Valid @RequestBody ApplyRequest request) {
        return ApiResponse.ok(merchantService.apply(request));
    }

    @GetMapping("/applications/me")
    public ApiResponse<MerchantView> profile() {
        try {
            return ApiResponse.ok(merchantService.myShop());
        } catch (BusinessException e) {
            // 没申请过不是错误，返回空即可，避免前端 404 噪音
            if (e.errorCode() == ErrorCode.SHOP_NOT_FOUND) {
                return ApiResponse.ok(null);
            }
            throw e;
        }
    }
}
