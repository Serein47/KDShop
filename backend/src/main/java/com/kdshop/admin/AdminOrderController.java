package com.kdshop.admin;

import com.kdshop.admin.dto.AdminOrderDetailView;
import com.kdshop.admin.dto.AdminOrderView;
import com.kdshop.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public ApiResponse<List<AdminOrderView>> list(@RequestParam(required = false) String orderNo,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminOrderService.list(orderNo, status, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<AdminOrderDetailView> detail(@PathVariable String orderNo) {
        return ApiResponse.ok(adminOrderService.detail(orderNo));
    }
}
