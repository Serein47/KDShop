package com.kdshop.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.MetaProperties;
import com.kdshop.order.dto.OrderDetailView;
import com.kdshop.order.dto.OrderListItem;
import com.kdshop.order.dto.OrderPage;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.entity.OrderStatusLog;
import com.kdshop.order.entity.Shipment;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import com.kdshop.order.mapper.ShipmentMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单读模型：列表与详情。
 * 规模假设是演示级订单量；列表先按用户取全量再内存分页，超过万级应改为 SQL 分页。
 */
@Service
public class OrderQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ORDER_STATUSES = Set.of(
            "PENDING_PAYMENT", "PAID", "PARTIALLY_SHIPPED", "SHIPPED", "COMPLETED", "CANCELLED", "REFUNDED");

    private final OrderMapper orderMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final ProductMapper productMapper;
    private final ShipmentMapper shipmentMapper;
    private final ObjectMapper objectMapper;
    private final MetaProperties meta;

    public OrderQueryService(OrderMapper orderMapper, MerchantOrderMapper merchantOrderMapper,
                             OrderItemMapper orderItemMapper, OrderStatusLogMapper statusLogMapper,
                             ProductMapper productMapper, ShipmentMapper shipmentMapper,
                             ObjectMapper objectMapper, MetaProperties meta) {
        this.orderMapper = orderMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.statusLogMapper = statusLogMapper;
        this.productMapper = productMapper;
        this.shipmentMapper = shipmentMapper;
        this.objectMapper = objectMapper;
        this.meta = meta;
    }

    public OrderPage list(String status, int page, int size) {
        Long userId = currentUserId();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        if (normalized != null && !ORDER_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "未知的订单状态：" + status);
        }

        List<Order> all = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(normalized != null, Order::getStatus, normalized)
                .orderByDesc(Order::getCreatedAt)
                .orderByDesc(Order::getId));

        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<OrderListItem> items = all.subList(from, to).stream().map(this::toListItem).toList();
        return new OrderPage(items, all.size(), safePage, safeSize, to < all.size());
    }

    public OrderDetailView detail(String orderNo) {
        Long userId = currentUserId();
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }

        List<MerchantOrder> subOrders = subOrders(order.getId());
        Map<Long, List<OrderItem>> itemsBySubOrder = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getMerchantOrderId));

        List<OrderDetailView.SubOrderView> subViews = new ArrayList<>();
        for (MerchantOrder sub : subOrders) {
            List<OrderItem> items = itemsBySubOrder.getOrDefault(sub.getId(), List.of());
            Shipment shipment = shipmentMapper.selectOne(new LambdaQueryWrapper<Shipment>()
                    .eq(Shipment::getMerchantOrderId, sub.getId()));
            List<String> actions = "SHIPPED".equals(sub.getStatus()) ? List.of("CONFIRM") : List.of();
            subViews.add(new OrderDetailView.SubOrderView(
                    sub.getSubOrderNo(),
                    sub.getMerchantId(),
                    items.isEmpty() ? "店铺 " + sub.getMerchantId() : items.get(0).getShopName(),
                    sub.getStatus(),
                    sub.getPayAmountCent(),
                    sub.getShippedAt(),
                    shipment == null ? null
                            : new OrderDetailView.ShipmentView(shipment.getCarrier(), shipment.getTrackingNo()),
                    items.stream().map(this::toItemView).toList(),
                    actions));
        }

        return new OrderDetailView(
                order.getOrderNo(),
                order.getStatus(),
                order.getGoodsAmountCent(),
                order.getDiscountCent(),
                order.getShippingFeeCent(),
                order.getPayAmountCent(),
                readAddress(order.getAddressJson()),
                order.getRemark(),
                order.getCreatedAt(),
                order.getPaidAt(),
                expireAt(order),
                timeline(order, subOrders),
                subViews);
    }

    private OrderListItem toListItem(Order order) {
        List<MerchantOrder> subOrders = subOrders(order.getId());
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        int shipped = (int) subOrders.stream()
                .filter(sub -> "SHIPPED".equals(sub.getStatus()) || "COMPLETED".equals(sub.getStatus()))
                .count();
        return new OrderListItem(
                order.getOrderNo(),
                order.getStatus(),
                order.getPayAmountCent(),
                order.getCreatedAt(),
                subOrders.size(),
                shipped,
                summary(items),
                coverImage(items),
                expireAt(order));
    }

    private List<MerchantOrder> subOrders(Long orderId) {
        return merchantOrderMapper.selectList(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, orderId)
                .orderByAsc(MerchantOrder::getId));
    }

    private OrderDetailView.ItemView toItemView(OrderItem item) {
        return new OrderDetailView.ItemView(item.getId(), item.getProductName(), item.getProductImage(), readMap(item.getSkuSpec()),
                item.getUnitPriceCent(), item.getQuantity(), item.getSubtotalCent());
    }

    private String summary(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "订单商品";
        }
        String first = items.get(0).getProductName();
        return items.size() == 1 ? first : first + " 等 " + items.size() + " 件商品";
    }

    private String coverImage(List<OrderItem> items) {
        if (items.isEmpty()) {
            return null;
        }
        Product product = productMapper.selectById(items.get(0).getProductId());
        return product == null ? null : product.getMainImage();
    }

    private LocalDateTime expireAt(Order order) {
        if (!"PENDING_PAYMENT".equals(order.getStatus()) || order.getCreatedAt() == null) {
            return null;
        }
        return order.getCreatedAt().plusMinutes(meta.payTimeoutMinutes());
    }

    /**
     * 时间线以状态流水为准；种子订单没有流水时，用订单与子单时间戳补齐，
     * 保证「下单 → 支付 → 发货 → 完成」不会因为缺少某条日志而断裂。
     */
    private List<OrderDetailView.TimelineEntry> timeline(Order order, List<MerchantOrder> subOrders) {
        Map<String, OrderDetailView.TimelineEntry> entries = new LinkedHashMap<>();
        putTimeline(entries, "PENDING_PAYMENT", order.getCreatedAt(), "USER");
        putTimeline(entries, "PAID", order.getPaidAt(), "SYSTEM");

        LocalDateTime shippedAt = subOrders.stream()
                .map(MerchantOrder::getShippedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (shippedAt != null) {
            String shippedStatus = "PARTIALLY_SHIPPED".equals(order.getStatus())
                    ? "PARTIALLY_SHIPPED" : "SHIPPED";
            putTimeline(entries, shippedStatus, shippedAt, "SYSTEM");
        }
        putTimeline(entries, "COMPLETED", order.getCompletedAt(), "SYSTEM");
        putTimeline(entries, "CANCELLED", order.getCancelledAt(), "USER");

        List<OrderStatusLog> logs = statusLogMapper.selectList(new LambdaQueryWrapper<OrderStatusLog>()
                .eq(OrderStatusLog::getScope, "ORDER")
                .eq(OrderStatusLog::getOrderId, order.getId())
                .orderByAsc(OrderStatusLog::getCreatedAt)
                .orderByAsc(OrderStatusLog::getId));
        for (OrderStatusLog log : logs) {
            putTimeline(entries, log.getToStatus(), log.getCreatedAt(), log.getOperatorType());
        }

        return entries.values().stream()
                .sorted(Comparator.comparing(OrderDetailView.TimelineEntry::at))
                .toList();
    }

    private void putTimeline(Map<String, OrderDetailView.TimelineEntry> entries, String status,
                             LocalDateTime at, String actor) {
        if (at != null) {
            entries.put(status, new OrderDetailView.TimelineEntry(status, at, actor));
        }
    }

    private OrderDetailView.AddressView readAddress(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OrderDetailView.AddressView.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
