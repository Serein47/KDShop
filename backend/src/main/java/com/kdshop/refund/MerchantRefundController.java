package com.kdshop.refund;

import com.kdshop.common.ApiResponse;
import com.kdshop.refund.dto.RefundView;
import com.kdshop.refund.dto.RejectRefundRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchant/refunds")
public class MerchantRefundController {

    private final RefundService refundService;

    public MerchantRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    public ApiResponse<List<RefundView>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(refundService.listForMerchant(status));
    }

    @PostMapping("/{refundNo}/approve")
    public ApiResponse<RefundView> approve(@PathVariable String refundNo) {
        return ApiResponse.ok(refundService.approve(refundNo));
    }

    @PostMapping("/{refundNo}/reject")
    public ApiResponse<RefundView> reject(@PathVariable String refundNo,
                                          @Valid @RequestBody RejectRefundRequest request) {
        return ApiResponse.ok(refundService.reject(refundNo, request.reason()));
    }
}
