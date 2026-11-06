package com.kdshop.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
import com.kdshop.merchant.dto.ShipRequest;
import com.kdshop.merchant.dto.SubOrderView;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.entity.OrderStatusLog;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 商家侧子单：查看本店待发货与历史，发货写物流信息并发通知。 */
@Service
public class MerchantOrderService {

    private final ShopMapper shopMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final EmailOutboxMapper emailOutboxMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public MerchantOrderService(ShopMapper shopMapper, MerchantOrderMapper merchantOrderMapper,
                                OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                OrderStatusLogMapper statusLogMapper, EmailOutboxMapper emailOutboxMapper,
                                UserMapper userMapper, ObjectMapper objectMapper) {
        this.shopMapper = shopMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.statusLogMapper = statusLogMapper;
        this.emailOutboxMapper = emailOutboxMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public List<SubOrderView> list(String status) {
        Shop shop = currentShop();
        LambdaQueryWrapper<MerchantOrder> query = new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getMerchantId, shop.getId())
                .orderByDesc(MerchantOrder::getCreatedAt)
                .last("LIMIT 100");
        if (StringUtils.hasText(status)) {
            query.eq(MerchantOrder::getStatus, status.toUpperCase());
        }
        List<MerchantOrder> subs = merchantOrderMapper.selectList(query);
        if (subs.isEmpty()) {
            return List.of();
        }
        Map<Long, Order> orders = orderMapper.selectBatchIds(
                        subs.stream().map(MerchantOrder::getOrderId).distinct().toList()).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));
        Map<Long, List<OrderItem>> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getMerchantOrderId, subs.stream().map(MerchantOrder::getId).toList()))
                .stream().collect(Collectors.groupingBy(OrderItem::getMerchantOrderId));

        return subs.stream().map(sub -> toView(sub, orders.get(sub.getOrderId()),
                items.getOrDefault(sub.getId(), List.of()))).toList();
    }

    @Transactional
    public SubOrderView ship(String subOrderNo, ShipRequest request) {
        Shop shop = currentShop();
        MerchantOrder sub = merchantOrderMapper.selectOne(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getSubOrderNo, subOrderNo));
        if (sub == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!sub.getMerchantId().equals(shop.getId())) {
            throw new BusinessException(ErrorCode.MERCHANT_SCOPE_FORBIDDEN);
        }
        if (!"PENDING_SHIPMENT".equals(sub.getStatus())) {
            throw new BusinessException(ErrorCode.SUB_ORDER_STATE_INVALID, "该子单当前不可发货");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = merchantOrderMapper.update(null, new LambdaUpdateWrapper<MerchantOrder>()
                .eq(MerchantOrder::getId, sub.getId())
                .eq(MerchantOrder::getStatus, "PENDING_SHIPMENT")
                .set(MerchantOrder::getStatus, "SHIPPED")
                .set(MerchantOrder::getShippedAt, now)
                .set(MerchantOrder::getCarrier, request.carrier())
                .set(MerchantOrder::getTrackingNo, request.trackingNo()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SUB_ORDER_STATE_INVALID, "该子单已被处理");
        }

        OrderStatusLog log = new OrderStatusLog();
        log.setScope("MERCHANT_ORDER");
        log.setOrderId(sub.getOrderId());
        log.setMerchantOrderId(sub.getId());
        log.setFromStatus("PENDING_SHIPMENT");
        log.setToStatus("SHIPPED");
        log.setOperatorType("MERCHANT");
        log.setOperatorId(shop.getId());
        log.setReason(request.carrier() + " " + request.trackingNo());
        statusLogMapper.insert(log);

        Order order = orderMapper.selectById(sub.getOrderId());
        if (order != null) {
            refreshOrderStatus(order);
            notifyShipped(order, sub, request, now);
        }

        MerchantOrder refreshed = merchantOrderMapper.selectById(sub.getId());
        Order latest = orderMapper.selectById(sub.getOrderId());
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getMerchantOrderId, sub.getId()));
        return toView(refreshed, latest, items);
    }

    private void refreshOrderStatus(Order order) {
        Long total = merchantOrderMapper.selectCount(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, order.getId()));
        Long shipped = merchantOrderMapper.selectCount(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, order.getId())
                .in(MerchantOrder::getStatus, List.of("SHIPPED", "COMPLETED")));
        String target = null;
        if (shipped != null && total != null && shipped.equals(total)) {
            target = "SHIPPED";
        } else if (shipped != null && shipped > 0) {
            target = "PARTIALLY_SHIPPED";
        }
        if (target == null || target.equals(order.getStatus())) {
            return;
        }
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .in(Order::getStatus, List.of("PAID", "PARTIALLY_SHIPPED", "SHIPPED"))
                .set(Order::getStatus, target));
    }

    private void notifyShipped(Order order, MerchantOrder sub, ShipRequest request, LocalDateTime now) {
        User user = userMapper.selectById(order.getUserId());
        EmailOutbox outbox = new EmailOutbox();
        outbox.setToEmail(user == null ? "unknown@kdshop.local" : user.getEmail());
        outbox.setTemplate("ORDER_SHIPPED");
        outbox.setBizKey("order_shipped:" + sub.getSubOrderNo());
        outbox.setStatus("PENDING");
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(Map.of(
                    "orderNo", order.getOrderNo(),
                    "carrier", request.carrier(),
                    "trackingNo", request.trackingNo(),
                    "shippedAt", now.toString(),
                    "orderUrl", "/orders/" + order.getOrderNo())));
        } catch (Exception e) {
            outbox.setPayloadJson("{}");
        }
        emailOutboxMapper.insert(outbox);
    }

    private SubOrderView toView(MerchantOrder sub, Order order, List<OrderItem> items) {
        return new SubOrderView(sub.getSubOrderNo(),
                order == null ? "" : order.getOrderNo(),
                sub.getStatus(), sub.getGoodsAmountCent(), sub.getDiscountCent(), sub.getShippingFeeCent(),
                sub.getPayAmountCent(), sub.getCreatedAt(), sub.getShippedAt(), sub.getCarrier(), sub.getTrackingNo(),
                order == null ? null : receiver(order), order == null ? null : phone(order),
                order == null ? null : address(order),
                items.stream().map(i -> new SubOrderView.Item(i.getProductName(), readMap(i.getSkuSpec()),
                        i.getUnitPriceCent(), i.getQuantity(), i.getSubtotalCent(), i.getProductImage())).toList());
    }

    private String receiver(Order order) { return field(order, "receiver"); }
    private String phone(Order order) { return field(order, "phone"); }

    private String address(Order order) {
        Map<String, Object> map = readObjectMap(order == null ? null : order.getAddressJson());
        return (str(map.get("province")) + str(map.get("city")) + str(map.get("district")) + str(map.get("detail")));
    }

    private String field(Order order, String key) {
        return str(readObjectMap(order == null ? null : order.getAddressJson()).get(key));
    }

    private Map<String, Object> readObjectMap(String json) {
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

    private Shop currentShop() {
        Long userId = currentUserId();
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, userId));
        if (shop == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_APPROVED, "还没有开通店铺");
        }
        if (!"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_APPROVED);
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
