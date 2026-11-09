package com.kdshop.refund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderStatusLog;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import com.kdshop.refund.dto.RefundView;
import com.kdshop.refund.entity.Refund;
import com.kdshop.refund.mapper.RefundMapper;
import com.kdshop.admin.AuditService;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** 退款：顾客按子单申请，商家审核；通过后退款金额计入子单已退金额。 */
@Service
public class RefundService {

    @Autowired(required = false)
    private AuditService auditService;

    @Autowired(required = false)
    private RoleMapper roleMapper;

    @Autowired(required = false)
    private UserRoleMapper userRoleMapper;

    private final RefundMapper refundMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderMapper orderMapper;
    private final ShopMapper shopMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final EmailOutboxMapper emailOutboxMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public RefundService(RefundMapper refundMapper, MerchantOrderMapper merchantOrderMapper, OrderMapper orderMapper,
                         ShopMapper shopMapper, OrderStatusLogMapper statusLogMapper,
                         EmailOutboxMapper emailOutboxMapper, UserMapper userMapper, ObjectMapper objectMapper) {
        this.refundMapper = refundMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderMapper = orderMapper;
        this.shopMapper = shopMapper;
        this.statusLogMapper = statusLogMapper;
        this.emailOutboxMapper = emailOutboxMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RefundView apply(String subOrderNo, String reason) {
        Long userId = currentUserId();
        MerchantOrder sub = merchantOrderMapper.selectOne(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getSubOrderNo, subOrderNo));
        if (sub == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        Order order = orderMapper.selectById(sub.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        if (!List.of("PENDING_SHIPMENT", "SHIPPED", "COMPLETED").contains(sub.getStatus())) {
            throw new BusinessException(ErrorCode.REFUND_STATE_INVALID, "该子单当前不可申请退款");
        }
        Long open = refundMapper.selectCount(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getMerchantOrderId, sub.getId())
                .in(Refund::getStatus, List.of("PENDING", "REFUNDED")));
        if (open != null && open > 0) {
            throw new BusinessException(ErrorCode.REFUND_STATE_INVALID, "该子单已有退款申请");
        }

        int amount = sub.getPayAmountCent() - (sub.getRefundedAmountCent() == null ? 0 : sub.getRefundedAmountCent());
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.REFUND_STATE_INVALID, "没有可退金额");
        }

        Refund refund = new Refund();
        refund.setRefundNo("RF" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000)));
        refund.setMerchantOrderId(sub.getId());
        refund.setOrderId(sub.getOrderId());
        refund.setMerchantId(sub.getMerchantId());
        refund.setAmountCent(amount);
        refund.setReason(reason);
        refund.setStatus("PENDING");
        refundMapper.insert(refund);

        OrderStatusLog log = new OrderStatusLog();
        log.setScope("MERCHANT_ORDER");
        log.setOrderId(sub.getOrderId());
        log.setMerchantOrderId(sub.getId());
        log.setFromStatus(sub.getStatus());
        log.setToStatus("REFUND_PENDING");
        log.setOperatorType("CUSTOMER");
        log.setOperatorId(userId);
        log.setReason("申请退款：" + reason);
        statusLogMapper.insert(log);

        return toView(refund, sub, order);
    }

    public List<RefundView> listMine() {
        Long userId = currentUserId();
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>().eq(Order::getUserId, userId));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        List<Refund> refunds = refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                .in(Refund::getOrderId, orderIds)
                .orderByDesc(Refund::getCreatedAt)
                .last("LIMIT 100"));
        return enrich(refunds);
    }

    public List<RefundView> listForMerchant(String status) {
        Shop shop = currentShop();
        LambdaQueryWrapper<Refund> query = new LambdaQueryWrapper<Refund>()
                .eq(Refund::getMerchantId, shop.getId())
                .orderByDesc(Refund::getCreatedAt)
                .last("LIMIT 100");
        if (StringUtils.hasText(status)) {
            query.eq(Refund::getStatus, status.toUpperCase());
        }
        return enrich(refundMapper.selectList(query));
    }

    @Transactional
    public RefundView approve(String refundNo) {
        Shop shop = currentShop();
        Refund refund = requireMerchantRefund(refundNo, shop.getId());
        if (!"PENDING".equals(refund.getStatus())) {
            throw new BusinessException(ErrorCode.REFUND_STATE_INVALID, "该退款单已处理");
        }
        LocalDateTime now = LocalDateTime.now();
        refund.setStatus("REFUNDED");
        refund.setFinishedAt(now);
        refund.setOperatorType("MERCHANT");
        refund.setOperatorId(shop.getId());
        refundMapper.updateById(refund);

        MerchantOrder sub = merchantOrderMapper.selectById(refund.getMerchantOrderId());
        if (sub != null) {
            int refunded = (sub.getRefundedAmountCent() == null ? 0 : sub.getRefundedAmountCent()) + refund.getAmountCent();
            MerchantOrder patch = new MerchantOrder();
            patch.setId(sub.getId());
            patch.setRefundedAmountCent(refunded);
            patch.setStatus(refunded >= sub.getPayAmountCent() ? "REFUNDED" : "PARTIALLY_REFUNDED");
            merchantOrderMapper.updateById(patch);
            refreshOrder(sub.getOrderId());
        }

        Order order = orderMapper.selectById(refund.getOrderId());
        writeLog(refund, "REFUNDED");
        if (auditService != null) {
            auditService.record("MERCHANT", shop.getId(), "REFUND_APPROVE", "REFUND",
                    refund.getRefundNo(), java.util.Map.of("status", "PENDING"), java.util.Map.of("status", "REFUNDED"));
        }
        notify(order, refund, "退款已完成", now);
        return toView(refund, sub, order);
    }

    @Transactional
    public RefundView reject(String refundNo, String reason) {
        Shop shop = currentShop();
        Refund refund = requireMerchantRefund(refundNo, shop.getId());
        if (!"PENDING".equals(refund.getStatus())) {
            throw new BusinessException(ErrorCode.REFUND_STATE_INVALID, "该退款单已处理");
        }
        refund.setStatus("REJECTED");
        refund.setRejectReason(reason);
        refund.setFinishedAt(LocalDateTime.now());
        refund.setOperatorType("MERCHANT");
        refund.setOperatorId(shop.getId());
        refundMapper.updateById(refund);

        Order order = orderMapper.selectById(refund.getOrderId());
        writeLog(refund, "REJECTED");
        if (auditService != null) {
            auditService.record("MERCHANT", shop.getId(), "REFUND_REJECT", "REFUND",
                    refund.getRefundNo(), java.util.Map.of("status", "PENDING"), java.util.Map.of("status", "REJECTED"));
        }
        notify(order, refund, "退款被驳回：" + reason, LocalDateTime.now());
        MerchantOrder sub = merchantOrderMapper.selectById(refund.getMerchantOrderId());
        return toView(refund, sub, order);
    }

    public List<RefundView> listAll(String status) {
        requireAdmin();
        LambdaQueryWrapper<Refund> query = new LambdaQueryWrapper<Refund>()
                .orderByDesc(Refund::getId)
                .last("LIMIT 100");
        if (StringUtils.hasText(status)) {
            query.eq(Refund::getStatus, status.toUpperCase());
        }
        return enrich(refundMapper.selectList(query));
    }

    @Transactional
    public RefundView intervene(String refundNo, boolean approve, String reason) {
        requireAdmin();
        Long adminId = currentUserId();
        Refund refund = refundMapper.selectOne(new LambdaQueryWrapper<Refund>().eq(Refund::getRefundNo, refundNo));
        if (refund == null) {
            throw new BusinessException(ErrorCode.REFUND_STATE_INVALID, "退款单不存在");
        }
        if (!"PENDING".equals(refund.getStatus())) {
            throw new BusinessException(ErrorCode.REFUND_STATE_INVALID, "该退款单已处理");
        }
        LocalDateTime now = LocalDateTime.now();
        refund.setStatus(approve ? "REFUNDED" : "REJECTED");
        refund.setOperatorType("ADMIN");
        refund.setOperatorId(adminId);
        refund.setFinishedAt(now);
        if (!approve) {
            refund.setRejectReason(reason == null || reason.isBlank() ? "平台裁决不予退款" : reason);
        }
        refundMapper.updateById(refund);

        MerchantOrder sub = merchantOrderMapper.selectById(refund.getMerchantOrderId());
        if (approve && sub != null) {
            int refunded = (sub.getRefundedAmountCent() == null ? 0 : sub.getRefundedAmountCent()) + refund.getAmountCent();
            MerchantOrder patch = new MerchantOrder();
            patch.setId(sub.getId());
            patch.setRefundedAmountCent(refunded);
            patch.setStatus(refunded >= sub.getPayAmountCent() ? "REFUNDED" : "PARTIALLY_REFUNDED");
            merchantOrderMapper.updateById(patch);
            refreshOrder(sub.getOrderId());
        }

        Order order = orderMapper.selectById(refund.getOrderId());
        writeLog(refund, refund.getStatus());
        if (auditService != null) {
            auditService.record("ADMIN", adminId, "REFUND_INTERVENE", "REFUND", refundNo,
                    java.util.Map.of("status", "PENDING"),
                    java.util.Map.of("status", refund.getStatus(), "reason", String.valueOf(reason)));
        }
        notify(order, refund, approve ? "平台裁决：退款已完成" : "平台裁决：不予退款", now);
        return toView(refund, sub, order);
    }

    private void requireAdmin() {
        Long userId = currentUserId();
        if (roleMapper == null || userRoleMapper == null) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
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

    private void refreshOrder(Long orderId) {
        Long total = merchantOrderMapper.selectCount(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, orderId));
        Long refunded = merchantOrderMapper.selectCount(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, orderId)
                .in(MerchantOrder::getStatus, List.of("REFUNDED", "CANCELLED")));
        String target = null;
        if (total != null && refunded != null && refunded.equals(total)) {
            target = "REFUNDED";
        } else if (refunded != null && refunded > 0) {
            target = "PARTIALLY_REFUNDED";
        }
        if (target != null) {
            orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, orderId)
                    .set(Order::getStatus, target));
        }
    }

    private void writeLog(Refund refund, String toStatus) {
        OrderStatusLog log = new OrderStatusLog();
        log.setScope("MERCHANT_ORDER");
        log.setOrderId(refund.getOrderId());
        log.setMerchantOrderId(refund.getMerchantOrderId());
        log.setFromStatus("REFUND_PENDING");
        log.setToStatus(toStatus);
        log.setOperatorType("MERCHANT");
        log.setOperatorId(refund.getMerchantId());
        log.setReason(refund.getStatus());
        statusLogMapper.insert(log);
    }

    private void notify(Order order, Refund refund, String result, LocalDateTime now) {
        if (order == null) {
            return;
        }
        User user = userMapper.selectById(order.getUserId());
        EmailOutbox outbox = new EmailOutbox();
        outbox.setToEmail(user == null ? "unknown@kdshop.local" : user.getEmail());
        outbox.setTemplate("REFUND_RESULT");
        outbox.setBizKey("refund_result:" + refund.getRefundNo());
        outbox.setStatus("PENDING");
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(Map.of(
                    "orderNo", order.getOrderNo(),
                    "refundNo", refund.getRefundNo(),
                    "amount", String.valueOf(refund.getAmountCent() / 100.0),
                    "result", result,
                    "orderUrl", "/orders/" + order.getOrderNo())));
        } catch (Exception e) {
            outbox.setPayloadJson("{}");
        }
        emailOutboxMapper.insert(outbox);
    }

    private Refund requireMerchantRefund(String refundNo, Long merchantId) {
        Refund refund = refundMapper.selectOne(new LambdaQueryWrapper<Refund>().eq(Refund::getRefundNo, refundNo));
        if (refund == null) {
            throw new BusinessException(ErrorCode.REFUND_STATE_INVALID, "退款单不存在");
        }
        if (!refund.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.MERCHANT_SCOPE_FORBIDDEN);
        }
        return refund;
    }

    private List<RefundView> enrich(List<Refund> refunds) {
        if (refunds.isEmpty()) {
            return List.of();
        }
        Map<Long, MerchantOrder> subs = merchantOrderMapper.selectBatchIds(
                        refunds.stream().map(Refund::getMerchantOrderId).toList()).stream()
                .collect(Collectors.toMap(MerchantOrder::getId, s -> s));
        Map<Long, Order> orders = orderMapper.selectBatchIds(
                        refunds.stream().map(Refund::getOrderId).distinct().toList()).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));
        Map<Long, Shop> shops = shopMapper.selectBatchIds(
                        refunds.stream().map(Refund::getMerchantId).distinct().toList()).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        return refunds.stream().map(r -> {
            MerchantOrder sub = subs.get(r.getMerchantOrderId());
            Order order = orders.get(r.getOrderId());
            Shop shop = shops.get(r.getMerchantId());
            return new RefundView(r.getRefundNo(), sub == null ? null : sub.getSubOrderNo(),
                    order == null ? null : order.getOrderNo(), r.getMerchantId(),
                    shop == null ? null : shop.getShopName(), r.getAmountCent(), r.getReason(),
                    r.getStatus(), r.getRejectReason(), r.getCreatedAt(), r.getFinishedAt());
        }).toList();
    }

    private RefundView toView(Refund refund, MerchantOrder sub, Order order) {
        Shop shop = shopMapper.selectById(refund.getMerchantId());
        return new RefundView(refund.getRefundNo(), sub == null ? null : sub.getSubOrderNo(),
                order == null ? null : order.getOrderNo(), refund.getMerchantId(),
                shop == null ? null : shop.getShopName(), refund.getAmountCent(), refund.getReason(),
                refund.getStatus(), refund.getRejectReason(), refund.getCreatedAt(), refund.getFinishedAt());
    }

    private Shop currentShop() {
        Long userId = currentUserId();
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, userId));
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_APPROVED, "没有开通店铺");
        }
        return shop;
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
