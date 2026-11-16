package com.kdshop.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.mail.MailOutboxWriter;
import com.kdshop.mail.MailTemplateType;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.entity.OrderStatusLog;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import com.kdshop.payment.entity.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 支付成功入账。分三条路径：
 * <ul>
 *   <li>FULL：一次付清 → 主单已付款、子单待发货、扣库存；</li>
 *   <li>DEPOSIT：定金 → 主单/子单制作中、写承诺发货时间、扣库存（ISSUE-34）；</li>
 *   <li>BALANCE：尾款 → 主单已付款、子单待发货，不再扣库存（ISSUE-35）。</li>
 * </ul>
 * 必须与支付单更新在同一个事务里，任一步失败整体回滚。
 */
@Service
public class OrderPaidService {

    private static final DateTimeFormatter MAIL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderMapper orderMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SkuMapper skuMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final MailOutboxWriter mailOutboxWriter;
    private final ProductMapper productMapper;

    public OrderPaidService(OrderMapper orderMapper, MerchantOrderMapper merchantOrderMapper,
                            OrderItemMapper orderItemMapper, SkuMapper skuMapper,
                            OrderStatusLogMapper statusLogMapper, MailOutboxWriter mailOutboxWriter,
                            ProductMapper productMapper) {
        this.orderMapper = orderMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.skuMapper = skuMapper;
        this.statusLogMapper = statusLogMapper;
        this.mailOutboxWriter = mailOutboxWriter;
        this.productMapper = productMapper;
    }

    @Transactional
    public boolean markPaid(Payment payment) {
        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        String purpose = payment.getPurpose() == null ? "FULL" : payment.getPurpose();
        return switch (purpose) {
            case "DEPOSIT" -> markDepositPaid(order, payment);
            case "BALANCE" -> markFullyPaid(order, payment, "PRODUCING", "PAID", "PENDING_SHIPMENT", false, true);
            default -> markFullyPaid(order, payment, "PENDING_PAYMENT", "PAID", "PENDING_SHIPMENT", true, false);
        };
    }

    /** 定金：主单与子单转「制作中」，子单写承诺发货时间，库存照常扣减。 */
    private boolean markDepositPaid(Order order, Payment payment) {
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        List<MerchantOrder> subs = merchantOrderMapper.selectList(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, order.getId()));
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        Map<Long, List<OrderItem>> bySub = items.stream()
                .collect(Collectors.groupingBy(OrderItem::getMerchantOrderId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Integer> leadDays = loadLeadDays(items);

        Map<Long, LocalDateTime> promisedBySub = new LinkedHashMap<>();
        LocalDateTime promised = now;
        for (MerchantOrder sub : subs) {
            LocalDateTime subPromised = now.plusDays(leadDaysFor(bySub.getOrDefault(sub.getId(), List.of()), leadDays));
            promisedBySub.put(sub.getId(), subPromised);
            if (subPromised.isAfter(promised)) {
                promised = subPromised;
            }
        }

        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, "PENDING_PAYMENT")
                .set(Order::getStatus, "PRODUCING")
                .set(Order::getPaidAt, now)
                .set(Order::getPromisedShipAt, promised));
        if (updated == 0) {
            return false;
        }

        for (MerchantOrder sub : subs) {
            merchantOrderMapper.update(null, new LambdaUpdateWrapper<MerchantOrder>()
                    .eq(MerchantOrder::getId, sub.getId())
                    .eq(MerchantOrder::getStatus, "PENDING_PAYMENT")
                    .set(MerchantOrder::getStatus, "PRODUCING")
                    .set(MerchantOrder::getPromisedShipAt, promisedBySub.get(sub.getId())));
        }

        deductStock(items);

        writeLog(order.getId(), null, "PENDING_PAYMENT", "PRODUCING", "deposit:" + payment.getPaymentNo());
        enqueuePaidMail(order, payment, "定金");
        return true;
    }

    private boolean markFullyPaid(Order order, Payment payment, String fromStatus, String toOrderStatus,
                                  String toSubStatus, boolean deductStock, boolean balancePayment) {
        if (!fromStatus.equals(order.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Order> update = new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, fromStatus)
                .set(Order::getStatus, toOrderStatus);
        if (balancePayment) {
            update.set(Order::getBalancePaidAt, now);
        } else {
            update.set(Order::getPaidAt, now);
        }
        int updated = orderMapper.update(null, update);
        if (updated == 0) {
            return false;
        }

        merchantOrderMapper.update(null, new LambdaUpdateWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, order.getId())
                .eq(MerchantOrder::getStatus, fromStatus)
                .set(MerchantOrder::getStatus, toSubStatus));

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        if (deductStock) {
            deductStock(items);
        }

        writeLog(order.getId(), null, fromStatus, toOrderStatus, "payment:" + payment.getPaymentNo());
        enqueuePaidMail(order, payment, "FULL".equals(payment.getPurpose()) ? null : "尾款");
        return true;
    }

    private void deductStock(List<OrderItem> items) {
        for (OrderItem item : items) {
            int deducted = skuMapper.deductStock(item.getSkuId(), item.getQuantity());
            if (deducted == 0) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                        "库存扣减失败：" + item.getProductName());
            }
        }
    }

    private Map<Long, Integer> loadLeadDays(List<OrderItem> items) {
        List<Long> productIds = items.stream().map(OrderItem::getProductId)
                .filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectBatchIds(productIds).stream()
                .filter(p -> p.getLeadDays() != null)
                .collect(Collectors.toMap(Product::getId, Product::getLeadDays));
    }

    private int leadDaysFor(List<OrderItem> subItems, Map<Long, Integer> leadDays) {
        return Math.max(subItems.stream()
                .mapToInt(item -> leadDays.getOrDefault(item.getProductId(), 1))
                .max().orElse(1), 1);
    }

    private void writeLog(Long orderId, Long merchantOrderId, String from, String to, String reason) {
        OrderStatusLog log = new OrderStatusLog();
        log.setScope(merchantOrderId == null ? "ORDER" : "MERCHANT_ORDER");
        log.setOrderId(orderId);
        log.setMerchantOrderId(merchantOrderId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorType("SYSTEM");
        log.setReason(reason);
        statusLogMapper.insert(log);
    }

    private void enqueuePaidMail(Order order, Payment payment, String label) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("orderNo", order.getOrderNo());
        vars.put("paidAmount", money(payment.getAmountCent()));
        vars.put("paidAt", LocalDateTime.now().format(MAIL_TIME));
        vars.put("orderUrl", "/orders/" + order.getOrderNo());
        vars.put("paymentLabel", label == null ? "" : "（" + label + "）");
        mailOutboxWriter.enqueueToUser(order.getUserId(), MailTemplateType.PAYMENT_SUCCESS,
                "payment_success:" + payment.getPaymentNo(), vars);
    }

    private String money(int cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
