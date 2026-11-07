package com.kdshop.admin;

import com.kdshop.admin.dto.AdminDashboardView;
import com.kdshop.admin.dto.AuditLogView;
import com.kdshop.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardView> dashboard() {
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLogView>> auditLogs(@RequestParam(required = false) String action,
                                                     @RequestParam(required = false) String actorType) {
        return ApiResponse.ok(adminService.auditLogs(action, actorType));
    }
}
