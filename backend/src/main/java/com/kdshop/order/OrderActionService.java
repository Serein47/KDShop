package com.kdshop.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.checkout.entity.UserCoupon;
import com.kdshop.checkout.mapper.UserCouponMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.order.dto.OrderCancelResult;
import com.kdshop.order.dto.SubOrderConfirmResult;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.entity.OrderStatusLog;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import com.kdshop.payment.entity.Payment;
import com.kdshop.payment.mapper.PaymentMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单动作：取消与确认收货。
 * 状态条件更新是并发下的唯一裁决点；副作用（库存、券、支付单、流水）与状态变更同事务。
 */
@Service
public class OrderActionService {

    private final OrderMapper orderMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final SkuMapper skuMapper;
    private final UserCouponMapper userCouponMapper;
    private final PaymentMapper paymentMapper;

    public OrderActionService(OrderMapper orderMapper, MerchantOrderMapper merchantOrderMapper,
                              OrderItemMapper orderItemMapper, OrderStatusLogMapper statusLogMapper,
                              SkuMapper skuMapper, UserCouponMapper userCouponMapper,
                              PaymentMapper paymentMapper) {
        this.orderMapper = orderMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.statusLogMapper = statusLogMapper;
        this.skuMapper = skuMapper;
        this.userCouponMapper = userCouponMapper;
        this.paymentMapper = paymentMapper;
    }

    @Transactional
    public OrderCancelResult cancel(String orderNo) {
        Long userId = currentUserId();
        Order order = requireOrder(orderNo, userId);
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "只有待付款订单可以取消");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, "PENDING_PAYMENT")
                .set(Order::getStatus, "CANCELLED")
                .set(Order::getCancelledAt, now)
                .set(Order::getCancelReason, "用户取消")
                .setSql("version = version + 1"));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单状态已变化，请刷新后重试");
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            if (skuMapper.unlockStock(item.getSkuId(), item.getQuantity()) == 0) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "库存释放失败：" + item.getProductName());
            }
        }

        merchantOrderMapper.update(null, new LambdaUpdateWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, order.getId())
                .eq(MerchantOrder::getStatus, "PENDING_PAYMENT")
                .set(MerchantOrder::getStatus, "CANCELLED"));

        if (order.getCouponId() != null) {
            userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .eq(UserCoupon::getOrderId, order.getId())
                    .eq(UserCoupon::getUserId, userId)
                    .eq(UserCoupon::getStatus, "USED")
                    .set(UserCoupon::getStatus, "UNUSED")
                    .set(UserCoupon::getOrderId, null)
                    .set(UserCoupon::getUsedAt, null));
        }

        paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId())
                .in(Payment::getStatus, "INIT", "PENDING")
                .set(Payment::getStatus, "CLOSED"));

        insertLog("ORDER", order.getId(), null, "PENDING_PAYMENT", "CANCELLED", "USER", userId, "用户取消");
        return new OrderCancelResult(orderNo, "CANCELLED");
    }

    @Transactional
    public SubOrderConfirmResult confirm(String orderNo, String subOrderNo) {
        Long userId = currentUserId();
        Order order = requireOrder(orderNo, userId);

        MerchantOrder sub = merchantOrderMapper.selectOne(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getSubOrderNo, subOrderNo)
                .eq(MerchantOrder::getOrderId, order.getId()));
        if (sub == null) {
            throw new BusinessException(ErrorCode.SUB_ORDER_STATE_INVALID, "子单不存在");
        }

        if ("COMPLETED".equals(sub.getStatus())) {
            return new SubOrderConfirmResult(subOrderNo, "COMPLETED",
                    recomputeOrderStatus(order, "confirm:" + subOrderNo));
        }
        if (!"SHIPPED".equals(sub.getStatus())) {
            throw new BusinessException(ErrorCode.SUB_ORDER_STATE_INVALID, "只有已发货的子单可以确认收货");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = merchantOrderMapper.update(null, new LambdaUpdateWrapper<MerchantOrder>()
                .eq(MerchantOrder::getId, sub.getId())
                .eq(MerchantOrder::getStatus, "SHIPPED")
                .set(MerchantOrder::getStatus, "COMPLETED")
                .set(MerchantOrder::getCompletedAt, now));
        if (updated == 0) {
            MerchantOrder latest = merchantOrderMapper.selectById(sub.getId());
            if (latest != null && "COMPLETED".equals(latest.getStatus())) {
                return new SubOrderConfirmResult(subOrderNo, "COMPLETED",
                        recomputeOrderStatus(order, "confirm:" + subOrderNo));
            }
            throw new BusinessException(ErrorCode.SUB_ORDER_STATE_INVALID, "子单状态已变化，请刷新后重试");
        }

        insertLog("MERCHANT_ORDER", order.getId(), sub.getId(),
                "SHIPPED", "COMPLETED", "USER", userId, "confirm:" + subOrderNo);
        String orderStatus = recomputeOrderStatus(order, "confirm:" + subOrderNo);
        return new SubOrderConfirmResult(subOrderNo, "COMPLETED", orderStatus);
    }

    private String recomputeOrderStatus(Order order, String reason) {
        List<MerchantOrder> subOrders = merchantOrderMapper.selectList(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, order.getId()));
        String next = resolveOrderStatus(subOrders, order.getStatus());
        if (next.equals(order.getStatus())) {
            return next;
        }

        LambdaUpdateWrapper<Order> update = new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, order.getStatus())
                .set(Order::getStatus, next);
        if ("COMPLETED".equals(next)) {
            update.set(Order::getCompletedAt, LocalDateTime.now());
        }
        int updated = orderMapper.update(null, update);
        if (updated == 0) {
            Order latest = orderMapper.selectById(order.getId());
            return latest == null ? order.getStatus() : latest.getStatus();
        }
        insertLog("ORDER", order.getId(), null, order.getStatus(), next, "USER", currentUserId(), reason);
        return next;
    }

    /**
     * 主单状态是子单的派生值。只处理当前已实现的子单状态；退款态的聚合留给售后 Issue。
     */
    private String resolveOrderStatus(List<MerchantOrder> subOrders, String current) {
        if (subOrders.isEmpty()) {
            return current;
        }
        boolean allCancelled = subOrders.stream().allMatch(sub -> "CANCELLED".equals(sub.getStatus()));
        boolean allRefunded = subOrders.stream().allMatch(sub -> "REFUNDED".equals(sub.getStatus()));
        boolean allCompleted = subOrders.stream().allMatch(sub -> "COMPLETED".equals(sub.getStatus()));
        boolean allShippedOrCompleted = subOrders.stream()
                .allMatch(sub -> "SHIPPED".equals(sub.getStatus()) || "COMPLETED".equals(sub.getStatus()));
        boolean anyShippedOrCompleted = subOrders.stream()
                .anyMatch(sub -> "SHIPPED".equals(sub.getStatus()) || "COMPLETED".equals(sub.getStatus()));
        boolean allPendingShipment = subOrders.stream()
                .allMatch(sub -> "PENDING_SHIPMENT".equals(sub.getStatus()));
        boolean allPendingPayment = subOrders.stream()
                .allMatch(sub -> "PENDING_PAYMENT".equals(sub.getStatus()));

        if (allCancelled) {
            return "CANCELLED";
        }
        if (allRefunded) {
            return "REFUNDED";
        }
        if (allCompleted) {
            return "COMPLETED";
        }
        if (allShippedOrCompleted) {
            return "SHIPPED";
        }
        if (anyShippedOrCompleted) {
            return "PARTIALLY_SHIPPED";
        }
        if (allPendingShipment) {
            return "PAID";
        }
        if (allPendingPayment) {
            return "PENDING_PAYMENT";
        }
        return current;
    }

    private void insertLog(String scope, Long orderId, Long merchantOrderId, String from, String to,
                           String operatorType, Long operatorId, String reason) {
        OrderStatusLog log = new OrderStatusLog();
        log.setScope(scope);
        log.setOrderId(orderId);
        log.setMerchantOrderId(merchantOrderId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setReason(reason);
        statusLogMapper.insert(log);
    }

    private Order requireOrder(String orderNo, Long userId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        return order;
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
