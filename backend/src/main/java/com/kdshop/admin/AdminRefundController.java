package com.kdshop.admin;

import com.kdshop.common.ApiResponse;
import com.kdshop.refund.RefundService;
import com.kdshop.refund.dto.RefundView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {

    private final RefundService refundService;

    public AdminRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    public ApiResponse<List<RefundView>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(refundService.listAll(status));
    }

    @PostMapping("/{refundNo}/intervene")
    public ApiResponse<RefundView> intervene(@PathVariable String refundNo,
                                             @RequestBody Map<String, Object> body) {
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return ApiResponse.ok(refundService.intervene(refundNo, approve, reason));
    }
}
