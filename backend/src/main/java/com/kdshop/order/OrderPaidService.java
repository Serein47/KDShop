package com.kdshop.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 支付成功入账：主单转已付款、子单转待发货、锁定库存转实扣、写状态流水与邮件。
 * 必须与支付单更新在同一个事务里，任一步失败整体回滚。
 */
@Service
public class OrderPaidService {

    private final OrderMapper orderMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SkuMapper skuMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final EmailOutboxMapper emailOutboxMapper;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;

    public OrderPaidService(OrderMapper orderMapper, MerchantOrderMapper merchantOrderMapper,
                            OrderItemMapper orderItemMapper, SkuMapper skuMapper,
                            OrderStatusLogMapper statusLogMapper, EmailOutboxMapper emailOutboxMapper,
                            ObjectMapper objectMapper, UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.skuMapper = skuMapper;
        this.statusLogMapper = statusLogMapper;
        this.emailOutboxMapper = emailOutboxMapper;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public boolean markPaid(Long orderId, String paymentNo) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, "PENDING_PAYMENT")
                .set(Order::getStatus, "PAID")
                .set(Order::getPaidAt, now));
        if (updated == 0) {
            return false;
        }

        merchantOrderMapper.update(null, new LambdaUpdateWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, orderId)
                .eq(MerchantOrder::getStatus, "PENDING_PAYMENT")
                .set(MerchantOrder::getStatus, "PENDING_SHIPMENT"));

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            int deducted = skuMapper.deductStock(item.getSkuId(), item.getQuantity());
            if (deducted == 0) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                        "库存扣减失败：" + item.getProductName());
            }
        }

        OrderStatusLog log = new OrderStatusLog();
        log.setScope("ORDER");
        log.setOrderId(orderId);
        log.setFromStatus("PENDING_PAYMENT");
        log.setToStatus("PAID");
        log.setOperatorType("SYSTEM");
        log.setReason("payment:" + paymentNo);
        statusLogMapper.insert(log);

        EmailOutbox outbox = new EmailOutbox();
        outbox.setToEmail(resolveEmail(order));
        outbox.setTemplate("PAYMENT_SUCCESS");
        outbox.setBizKey("payment_success:" + paymentNo);
        outbox.setStatus("PENDING");
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(Map.of(
                    "orderNo", order.getOrderNo(),
                    "paidAmount", order.getPayAmountCent(),
                    "paidAt", now.toString())));
        } catch (Exception e) {
            outbox.setPayloadJson("{}");
        }
        emailOutboxMapper.insert(outbox);
        return true;
    }

    private String resolveEmail(Order order) {
        User user = userMapper.selectById(order.getUserId());
        return user == null ? "unknown@kdshop.local" : user.getEmail();
    }
}
