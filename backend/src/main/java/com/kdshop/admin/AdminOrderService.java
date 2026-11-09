package com.kdshop.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.admin.dto.AdminOrderDetailView;
import com.kdshop.admin.dto.AdminOrderView;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.refund.entity.Refund;
import com.kdshop.refund.mapper.RefundMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 平台订单检索：跨用户、跨店铺查询与详情。 */
@Service
public class AdminOrderService {

    private final OrderMapper orderMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;
    private final RefundMapper refundMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final ObjectMapper objectMapper;

    public AdminOrderService(OrderMapper orderMapper, MerchantOrderMapper merchantOrderMapper, ShopMapper shopMapper,
                             UserMapper userMapper, RefundMapper refundMapper, RoleMapper roleMapper,
                             UserRoleMapper userRoleMapper, ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
        this.refundMapper = refundMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.objectMapper = objectMapper;
    }

    public List<AdminOrderView> list(String orderNo, String status, int page, int size) {
        requireAdmin();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<Order>()
                .orderByDesc(Order::getId)
                .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize);
        if (StringUtils.hasText(orderNo)) {
            query.like(Order::getOrderNo, orderNo);
        }
        if (StringUtils.hasText(status)) {
            query.eq(Order::getStatus, status.toUpperCase());
        }
        List<Order> orders = orderMapper.selectList(query);
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(
                        orders.stream().map(Order::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return orders.stream().map(o -> {
            User user = users.get(o.getUserId());
            return new AdminOrderView(o.getOrderNo(), o.getUserId(), user == null ? null : user.getNickname(),
                    o.getStatus(), o.getMerchantCount() == null ? 0 : o.getMerchantCount(),
                    o.getGoodsAmountCent() == null ? 0 : o.getGoodsAmountCent(),
                    o.getPayAmountCent() == null ? 0 : o.getPayAmountCent(),
                    o.getCreatedAt(), o.getPaidAt());
        }).toList();
    }

    public AdminOrderDetailView detail(String orderNo) {
        requireAdmin();
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        List<MerchantOrder> subs = merchantOrderMapper.selectList(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, order.getId())
                .orderByAsc(MerchantOrder::getId));
        Map<Long, Shop> shops = subs.isEmpty() ? Map.of() : shopMapper.selectBatchIds(
                        subs.stream().map(MerchantOrder::getMerchantId).distinct().toList()).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        Map<Long, String> subNos = subs.stream()
                .collect(Collectors.toMap(MerchantOrder::getId, MerchantOrder::getSubOrderNo));

        List<Refund> refunds = refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, order.getId())
                .orderByDesc(Refund::getId));

        Map<String, Object> address = readAddress(order.getAddressJson());
        User user = userMapper.selectById(order.getUserId());

        return new AdminOrderDetailView(order.getOrderNo(), order.getUserId(),
                user == null ? null : user.getNickname(), order.getStatus(),
                order.getGoodsAmountCent() == null ? 0 : order.getGoodsAmountCent(),
                order.getDiscountCent() == null ? 0 : order.getDiscountCent(),
                order.getShippingFeeCent() == null ? 0 : order.getShippingFeeCent(),
                order.getPayAmountCent() == null ? 0 : order.getPayAmountCent(),
                str(address.get("receiver")), str(address.get("phone")),
                str(address.get("province")) + str(address.get("city")) + str(address.get("district")) + str(address.get("detail")),
                order.getCreatedAt(), order.getPaidAt(),
                subs.stream().map(s -> new AdminOrderDetailView.Sub(s.getSubOrderNo(), s.getMerchantId(),
                        shops.get(s.getMerchantId()) == null ? null : shops.get(s.getMerchantId()).getShopName(),
                        s.getStatus(), s.getPayAmountCent(), s.getRefundedAmountCent() == null ? 0 : s.getRefundedAmountCent(),
                        s.getCarrier(), s.getTrackingNo(), s.getShippedAt())).toList(),
                refunds.stream().map(r -> new AdminOrderDetailView.Refund(r.getRefundNo(),
                        subNos.get(r.getMerchantOrderId()), r.getAmountCent(), r.getStatus(), r.getReason(),
                        r.getRejectReason(), r.getCreatedAt())).toList());
    }

    private Map<String, Object> readAddress(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String str(Object value) {
        return value == null ? "" : value.toString();
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
