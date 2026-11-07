package com.kdshop.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.admin.dto.AdminDashboardView;
import com.kdshop.admin.dto.AuditLogView;
import com.kdshop.admin.entity.AuditLog;
import com.kdshop.admin.mapper.AuditLogMapper;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.refund.entity.Refund;
import com.kdshop.refund.mapper.RefundMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 平台看板与审计日志查询。 */
@Service
public class AdminService {

    private final OrderMapper orderMapper;
    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final RefundMapper refundMapper;
    private final AuditLogMapper auditLogMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public AdminService(OrderMapper orderMapper, ShopMapper shopMapper, ProductMapper productMapper,
                        UserMapper userMapper, RefundMapper refundMapper, AuditLogMapper auditLogMapper,
                        UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.orderMapper = orderMapper;
        this.shopMapper = shopMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.refundMapper = refundMapper;
        this.auditLogMapper = auditLogMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    public AdminDashboardView dashboard() {
        requireAdmin();

        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>());
        List<Order> paid = orders.stream().filter(o -> o.getPaidAt() != null && !"CANCELLED".equals(o.getStatus())).toList();
        int gmv = paid.stream().mapToInt(o -> o.getPayAmountCent() == null ? 0 : o.getPayAmountCent()).sum();

        List<Refund> refunds = refundMapper.selectList(new LambdaQueryWrapper<Refund>().eq(Refund::getStatus, "REFUNDED"));
        int refunded = refunds.stream().mapToInt(r -> r.getAmountCent() == null ? 0 : r.getAmountCent()).sum();
        int netGmv = Math.max(gmv - refunded, 0);

        Long active = shopMapper.selectCount(new LambdaQueryWrapper<Shop>().eq(Shop::getStatus, "ACTIVE"));
        Long pending = shopMapper.selectCount(new LambdaQueryWrapper<Shop>().eq(Shop::getStatus, "PENDING"));
        Long products = productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getStatus, "ON"));
        Long customers = userMapper.selectCount(new LambdaQueryWrapper<com.kdshop.auth.entity.User>());

        LocalDate today = LocalDate.now();
        Map<String, int[]> buckets = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            buckets.put(today.minusDays(i).toString(), new int[]{0, 0});
        }
        for (Order order : paid) {
            if (order.getCreatedAt() == null) {
                continue;
            }
            int[] bucket = buckets.get(order.getCreatedAt().toLocalDate().toString());
            if (bucket != null) {
                bucket[0] += 1;
                bucket[1] += order.getPayAmountCent() == null ? 0 : order.getPayAmountCent();
            }
        }
        List<AdminDashboardView.TrendPoint> trend = buckets.entrySet().stream()
                .map(e -> new AdminDashboardView.TrendPoint(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .collect(Collectors.toList());

        int commission = (int) Math.round(netGmv * 0.05);

        return new AdminDashboardView(netGmv, commission, orders.size(), paid.size(),
                active == null ? 0 : active.intValue(), pending == null ? 0 : pending.intValue(),
                customers == null ? 0 : customers.intValue(), products == null ? 0 : products.intValue(), trend);
    }

    public List<AuditLogView> auditLogs(String action, String actorType) {
        requireAdmin();
        LambdaQueryWrapper<AuditLog> query = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getId)
                .last("LIMIT 100");
        if (StringUtils.hasText(action)) {
            query.eq(AuditLog::getAction, action);
        }
        if (StringUtils.hasText(actorType)) {
            query.eq(AuditLog::getActorType, actorType.toUpperCase());
        }
        return auditLogMapper.selectList(query).stream()
                .map(log -> new AuditLogView(log.getId(), log.getActorType(), log.getActorId(), log.getAction(),
                        log.getTargetType(), log.getTargetId(), log.getBeforeJson(), log.getAfterJson(),
                        log.getIp(), log.getCreatedAt()))
                .toList();
    }

    private void requireAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "ADMIN"));
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId()));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }
}
