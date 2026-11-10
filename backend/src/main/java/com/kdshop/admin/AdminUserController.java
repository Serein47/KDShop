package com.kdshop.admin;

import com.kdshop.admin.dto.AdminUserView;
import com.kdshop.admin.dto.AssignRolesRequest;
import com.kdshop.common.ApiResponse;
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
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<List<AdminUserView>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status) {
        return ApiResponse.ok(adminUserService.list(keyword, status));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<AdminUserView> disable(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(adminUserService.setStatus(id, false, reason));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<AdminUserView> enable(@PathVariable Long id) {
        return ApiResponse.ok(adminUserService.setStatus(id, true, null));
    }

    @PostMapping("/{id}/roles")
    public ApiResponse<AdminUserView> assignRoles(@PathVariable Long id, @RequestBody AssignRolesRequest request) {
        return ApiResponse.ok(adminUserService.assignRoles(id, request.roles()));
    }
}
