package com.kdshop.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.checkout.entity.UserCoupon;
import com.kdshop.checkout.mapper.UserCouponMapper;
import com.kdshop.config.MetaProperties;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 定时维护：未支付超时取消（释放库存、退券）、发货后自动确认收货。
 * 单实例部署，直接 @Scheduled；多实例时再加分布式锁。
 */
@Service
public class OrderMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(OrderMaintenanceService.class);
    private static final DateTimeFormatter MAIL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderMapper orderMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SkuMapper skuMapper;
    private final UserCouponMapper userCouponMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final EmailOutboxMapper emailOutboxMapper;
    private final MetaProperties meta;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final PaymentMapper paymentMapper;

    public OrderMaintenanceService(OrderMapper orderMapper, MerchantOrderMapper merchantOrderMapper,
                                   OrderItemMapper orderItemMapper, SkuMapper skuMapper,
                                   UserCouponMapper userCouponMapper, OrderStatusLogMapper statusLogMapper,
                                   EmailOutboxMapper emailOutboxMapper, MetaProperties meta,
                                   TransactionTemplate tx, ObjectMapper objectMapper, UserMapper userMapper,
                                   PaymentMapper paymentMapper) {
        this.orderMapper = orderMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.skuMapper = skuMapper;
        this.userCouponMapper = userCouponMapper;
        this.statusLogMapper = statusLogMapper;
        this.emailOutboxMapper = emailOutboxMapper;
        this.meta = meta;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
        this.paymentMapper = paymentMapper;
    }

    @Scheduled(fixedDelayString = "${kdshop.jobs.scan-interval-ms:60000}", initialDelay = 20000)
    public void scan() {
        int cancelled = cancelExpiredOrders();
        int confirmed = autoConfirmShipped();
        int notified = notifyBalanceDue();
        int closed = closeOverdueBalance();
        if (cancelled > 0 || confirmed > 0 || notified > 0 || closed > 0) {
            log.info("order maintenance: cancelled={}, autoConfirmed={}, balanceNotified={}, balanceClosed={}",
                    cancelled, confirmed, notified, closed);
        }
    }

    public int cancelExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oldestDeadline = now.minusMinutes(meta.payTimeoutMinutes());
        List<Order> candidates = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "PENDING_PAYMENT")
                .lt(Order::getCreatedAt, oldestDeadline)
                .last("LIMIT 50"));
        int count = 0;
        for (Order order : candidates) {
            // 预售/定制给 24 小时付定金，普通订单 15 分钟；查询先用最紧的线捞出来再逐单判断
            if (now.isBefore(payDeadline(order))) {
                continue;
            }
            Boolean done = tx.execute(status -> cancelOne(order));
            if (Boolean.TRUE.equals(done)) {
                count++;
            }
        }
        return count;
    }

    private LocalDateTime payDeadline(Order order) {
        boolean preorder = "PREORDER".equals(order.getOrderType()) || "CUSTOM".equals(order.getOrderType());
        return preorder
                ? order.getCreatedAt().plusHours(meta.preorderPayTimeoutHours())
                : order.getCreatedAt().plusMinutes(meta.payTimeoutMinutes());
    }

    /** 承诺发货前 N 天提醒付尾款；同一订单只提醒一次。 */
    public int notifyBalanceDue() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusDays(meta.balanceNotifyDaysBefore());
        List<Order> candidates = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "PRODUCING")
                .isNull(Order::getBalanceNotifiedAt)
                .le(Order::getPromisedShipAt, threshold)
                .last("LIMIT 50"));
        int count = 0;
        for (Order order : candidates) {
            if (order.getPromisedShipAt() == null
                    || order.getPromisedShipAt().isAfter(threshold)
                    || order.getBalanceCent() == null
                    || order.getBalanceCent() <= 0) {
                continue;
            }
            Integer done = tx.execute(status -> notifyBalanceOne(order));
            if (done != null && done > 0) {
                count++;
            }
        }
        return count;
    }

    private Integer notifyBalanceOne(Order order) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueAt = now.plusDays(meta.balancePayDays());
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, "PRODUCING")
                .isNull(Order::getBalanceNotifiedAt)
                .set(Order::getBalanceNotifiedAt, now)
                .set(Order::getBalanceDueAt, dueAt));
        if (updated == 0) {
            return 0;
        }

        OrderStatusLog logRow = new OrderStatusLog();
        logRow.setScope("ORDER");
        logRow.setOrderId(order.getId());
        logRow.setFromStatus("PRODUCING");
        logRow.setToStatus("PRODUCING");
        logRow.setOperatorType("SYSTEM");
        logRow.setReason("BALANCE_NOTIFIED");
        statusLogMapper.insert(logRow);

        EmailOutbox outbox = new EmailOutbox();
        User user = userMapper.selectById(order.getUserId());
        outbox.setToEmail(user == null ? "unknown@kdshop.local" : user.getEmail());
        outbox.setTemplate("ORDER_BALANCE_DUE");
        outbox.setBizKey("order_balance_due:" + order.getOrderNo());
        outbox.setStatus("PENDING");
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(Map.of(
                    "orderNo", order.getOrderNo(),
                    "balance", BigDecimal.valueOf(order.getBalanceCent(), 2).toPlainString(),
                    "dueAt", dueAt.format(MAIL_TIME),
                    "payUrl", "/orders/" + order.getOrderNo())));
        } catch (Exception e) {
            outbox.setPayloadJson("{}");
        }
        emailOutboxMapper.insert(outbox);
        return 1;
    }

    /** 尾款逾期：关闭订单，定金不退、库存不回补、优惠券不退还。 */
    public int closeOverdueBalance() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> candidates = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "PRODUCING")
                .isNotNull(Order::getBalanceDueAt)
                .lt(Order::getBalanceDueAt, now)
                .last("LIMIT 50"));
        int count = 0;
        for (Order order : candidates) {
            if (order.getBalanceDueAt() == null || now.isBefore(order.getBalanceDueAt())) {
                continue;
            }
            Boolean done = tx.execute(status -> closeOverdueOne(order));
            if (Boolean.TRUE.equals(done)) {
                count++;
            }
        }
        return count;
    }

    private boolean closeOverdueOne(Order order) {
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, "PRODUCING")
                .lt(Order::getBalanceDueAt, now)
                .set(Order::getStatus, "CANCELLED")
                .set(Order::getCancelledAt, now)
                .set(Order::getCancelReason, "BALANCE_OVERDUE"));
        if (updated == 0) {
            return false;
        }

        merchantOrderMapper.update(null, new LambdaUpdateWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, order.getId())
                .eq(MerchantOrder::getStatus, "PRODUCING")
                .set(MerchantOrder::getStatus, "CANCELLED"));

        paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId())
                .eq(Payment::getStatus, "PENDING")
                .set(Payment::getStatus, "CLOSED"));

        OrderStatusLog logRow = new OrderStatusLog();
        logRow.setScope("ORDER");
        logRow.setOrderId(order.getId());
        logRow.setFromStatus("PRODUCING");
        logRow.setToStatus("CANCELLED");
        logRow.setOperatorType("SYSTEM");
        logRow.setReason("BALANCE_OVERDUE");
        statusLogMapper.insert(logRow);

        EmailOutbox outbox = new EmailOutbox();
        User user = userMapper.selectById(order.getUserId());
        outbox.setToEmail(user == null ? "unknown@kdshop.local" : user.getEmail());
        outbox.setTemplate("ORDER_CANCELLED");
        outbox.setBizKey("order_cancelled:" + order.getOrderNo());
        outbox.setStatus("PENDING");
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(Map.of(
                    "orderNo", order.getOrderNo(),
                    "reason", "尾款逾期未付，定金不予退还",
                    "cancelledAt", now.toString(),
                    "orderUrl", "/orders/" + order.getOrderNo())));
        } catch (Exception e) {
            outbox.setPayloadJson("{}");
        }
        emailOutboxMapper.insert(outbox);
        return true;
    }

    private boolean cancelOne(Order order) {
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, "PENDING_PAYMENT")
                .set(Order::getStatus, "CANCELLED")
                .set(Order::getCancelledAt, now)
                .set(Order::getCancelReason, "PAY_TIMEOUT"));
        if (updated == 0) {
            return false;
        }

        merchantOrderMapper.update(null, new LambdaUpdateWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, order.getId())
                .eq(MerchantOrder::getStatus, "PENDING_PAYMENT")
                .set(MerchantOrder::getStatus, "CANCELLED"));

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            skuMapper.unlockStock(item.getSkuId(), item.getQuantity());
        }

        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getOrderId, order.getId())
                .eq(UserCoupon::getStatus, "USED")
                .set(UserCoupon::getStatus, "UNUSED")
                .set(UserCoupon::getOrderId, null)
                .set(UserCoupon::getUsedAt, null));

        OrderStatusLog logRow = new OrderStatusLog();
        logRow.setScope("ORDER");
        logRow.setOrderId(order.getId());
        logRow.setFromStatus("PENDING_PAYMENT");
        logRow.setToStatus("CANCELLED");
        logRow.setOperatorType("SYSTEM");
        logRow.setReason("PAY_TIMEOUT");
        statusLogMapper.insert(logRow);

        EmailOutbox outbox = new EmailOutbox();
        User user = userMapper.selectById(order.getUserId());
        outbox.setToEmail(user == null ? "unknown@kdshop.local" : user.getEmail());
        outbox.setTemplate("ORDER_CANCELLED");
        outbox.setBizKey("order_cancelled:" + order.getOrderNo());
        outbox.setStatus("PENDING");
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(Map.of(
                    "orderNo", order.getOrderNo(),
                    "reason", "超时未支付",
                    "cancelledAt", now.toString(),
                    "orderUrl", "/orders/" + order.getOrderNo())));
        } catch (Exception e) {
            outbox.setPayloadJson("{}");
        }
        emailOutboxMapper.insert(outbox);
        return true;
    }

    public int autoConfirmShipped() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(meta.autoConfirmDays());
        List<MerchantOrder> shipped = merchantOrderMapper.selectList(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getStatus, "SHIPPED")
                .lt(MerchantOrder::getShippedAt, cutoff)
                .last("LIMIT 50"));
        int count = 0;
        for (MerchantOrder sub : shipped) {
            Boolean done = tx.execute(status -> confirmOne(sub));
            if (Boolean.TRUE.equals(done)) {
                count++;
            }
        }
        return count;
    }

    private boolean confirmOne(MerchantOrder sub) {
        LocalDateTime now = LocalDateTime.now();
        int updated = merchantOrderMapper.update(null, new LambdaUpdateWrapper<MerchantOrder>()
                .eq(MerchantOrder::getId, sub.getId())
                .eq(MerchantOrder::getStatus, "SHIPPED")
                .set(MerchantOrder::getStatus, "COMPLETED")
                .set(MerchantOrder::getCompletedAt, now));
        if (updated == 0) {
            return false;
        }

        OrderStatusLog logRow = new OrderStatusLog();
        logRow.setScope("MERCHANT_ORDER");
        logRow.setOrderId(sub.getOrderId());
        logRow.setMerchantOrderId(sub.getId());
        logRow.setFromStatus("SHIPPED");
        logRow.setToStatus("COMPLETED");
        logRow.setOperatorType("SYSTEM");
        logRow.setReason("AUTO_CONFIRM");
        statusLogMapper.insert(logRow);

        Long remaining = merchantOrderMapper.selectCount(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, sub.getOrderId())
                .ne(MerchantOrder::getStatus, "COMPLETED"));
        if (remaining == null || remaining == 0) {
            orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, sub.getOrderId())
                    .eq(Order::getStatus, "PAID")
                    .set(Order::getStatus, "COMPLETED")
                    .set(Order::getCompletedAt, now));
        }
        return true;
    }
}
