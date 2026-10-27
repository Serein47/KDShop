package com.kdshop.meta;

import com.kdshop.common.ApiResponse;
import com.kdshop.config.MetaProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 公开配置接口：前端启动时读取一次，见 docs/接口文档/01-公共与认证.md。 */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

    private final MetaProperties properties;

    public MetaController(MetaProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/meta")
    public ApiResponse<MetaView> meta() {
        MetaView view = new MetaView(
                properties.currency(),
                properties.timezone(),
                new Shipping(properties.shippingFreeThresholdCent(), properties.shippingFeeCent(), true),
                new OrderPolicy(properties.payTimeoutMinutes(), properties.autoConfirmDays()),
                new Settlement(properties.settlementProtectionDays()),
                new Features(true, true, true, false)
        );
        return ApiResponse.ok(view);
    }

    public record MetaView(String currency, String timezone, Shipping shipping,
                           OrderPolicy order, Settlement settlement, Features features) {
    }

    public record Shipping(int freeThresholdCent, int feeCent, boolean perMerchant) {
    }

    public record OrderPolicy(int payTimeoutMinutes, int autoConfirmDays) {
    }

    public record Settlement(int protectionDays) {
    }

    public record Features(boolean coupon, boolean review, boolean favorite, boolean agent) {
    }
}
