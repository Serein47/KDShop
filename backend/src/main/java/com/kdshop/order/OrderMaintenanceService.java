package com.kdshop.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 定时维护：未支付超时取消（释放库存、退券）、发货后自动确认收货。
 * 单实例部署，直接 @Scheduled；多实例时再加分布式锁。
 */
@Service
public class OrderMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(OrderMaintenanceService.class);

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

    public OrderMaintenanceService(OrderMapper orderMapper, MerchantOrderMapper merchantOrderMapper,
                                   OrderItemMapper orderItemMapper, SkuMapper skuMapper,
                                   UserCouponMapper userCouponMapper, OrderStatusLogMapper statusLogMapper,
                                   EmailOutboxMapper emailOutboxMapper, MetaProperties meta,
                                   TransactionTemplate tx, ObjectMapper objectMapper) {
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
    }

    @Scheduled(fixedDelayString = "${kdshop.jobs.scan-interval-ms:60000}", initialDelay = 20000)
    public void scan() {
        int cancelled = cancelExpiredOrders();
        int confirmed = autoConfirmShipped();
        if (cancelled > 0 || confirmed > 0) {
            log.info("order maintenance: cancelled={}, autoConfirmed={}", cancelled, confirmed);
        }
    }

    public int cancelExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(meta.payTimeoutMinutes());
        List<Order> expired = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "PENDING_PAYMENT")
                .lt(Order::getCreatedAt, deadline)
                .last("LIMIT 50"));
        int count = 0;
        for (Order order : expired) {
            Boolean done = tx.execute(status -> cancelOne(order));
            if (Boolean.TRUE.equals(done)) {
                count++;
            }
        }
        return count;
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
        outbox.setToEmail("order-" + order.getId() + "@kdshop.local");
        outbox.setTemplate("ORDER_CANCELLED");
        outbox.setBizKey("order_cancelled:" + order.getOrderNo());
        outbox.setStatus("PENDING");
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(Map.of(
                    "orderNo", order.getOrderNo(), "reason", "PAY_TIMEOUT")));
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
