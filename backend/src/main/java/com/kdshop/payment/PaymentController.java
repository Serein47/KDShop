package com.kdshop.payment;

import com.kdshop.common.ApiResponse;
import com.kdshop.payment.dto.CreatePaymentRequest;
import com.kdshop.payment.dto.MockCompleteRequest;
import com.kdshop.payment.dto.PaymentView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ApiResponse<PaymentView> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.ok(paymentService.create(request.orderNo()));
    }

    @GetMapping("/{paymentNo}")
    public ApiResponse<PaymentView> status(@PathVariable String paymentNo) {
        return ApiResponse.ok(paymentService.status(paymentNo));
    }

    /** 模拟网关页面的按钮：服务端签名后走回调。 */
    @PostMapping("/mock/complete")
    public ApiResponse<PaymentView> complete(@Valid @RequestBody MockCompleteRequest request) {
        return ApiResponse.ok(paymentService.completeOnMockPage(request.paymentNo(), request.success()));
    }

    /** 网关 → 平台的回调（无需登录，靠签名）。 */
    @PostMapping("/callback/mock")
    public Map<String, Object> callback(@RequestBody Map<String, String> payload) {
        return paymentService.handleCallback(payload);
    }
}
