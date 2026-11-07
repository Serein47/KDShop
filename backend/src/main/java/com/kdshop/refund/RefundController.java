package com.kdshop.refund;

import com.kdshop.common.ApiResponse;
import com.kdshop.refund.dto.ApplyRefundRequest;
import com.kdshop.refund.dto.RefundView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    public ApiResponse<RefundView> apply(@Valid @RequestBody ApplyRefundRequest request) {
        return ApiResponse.ok(refundService.apply(request.subOrderNo(), request.reason()));
    }

    @GetMapping
    public ApiResponse<List<RefundView>> mine() {
        return ApiResponse.ok(refundService.listMine());
    }
}
