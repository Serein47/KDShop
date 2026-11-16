package com.kdshop.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.cart.entity.CartItem;
import com.kdshop.cart.mapper.CartItemMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.checkout.CheckoutService;
import com.kdshop.checkout.dto.CheckoutPreview;
import com.kdshop.checkout.dto.CheckoutRequest;
import com.kdshop.checkout.entity.UserCoupon;
import com.kdshop.checkout.mapper.UserCouponMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.MetaProperties;
import com.kdshop.mail.MailOutboxWriter;
import com.kdshop.mail.MailTemplateType;
import com.kdshop.order.dto.CreateOrderRequest;
import com.kdshop.order.dto.OrderCreatedResult;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.entity.OrderItemOption;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderItemOptionMapper;
import com.kdshop.order.mapper.OrderMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 下单：事务内建主单/子单/快照、锁库存、占券，并保证幂等。
 * 金额与拆单完全复用结算预览，避免两套算法漂移。
 */
@Service
public class OrderService {

    private static final String IDEM_PREFIX = "idem:order:";
    private static final String SEQ_PREFIX = "order:seq:";
    private static final Duration IDEM_TTL = Duration.ofHours(24);
    private static final DateTimeFormatter MAIL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CheckoutService checkoutService;
    private final SkuMapper skuMapper;
    private final CartItemMapper cartItemMapper;
    private final UserCouponMapper userCouponMapper;
    private final OrderMapper orderMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderItemOptionMapper orderItemOptionMapper;
    private final StringRedisTemplate redis;
    private final MetaProperties meta;
    private final ObjectMapper objectMapper;
    private final MailOutboxWriter mailOutboxWriter;

    public OrderService(CheckoutService checkoutService, SkuMapper skuMapper, CartItemMapper cartItemMapper,
                        UserCouponMapper userCouponMapper, OrderMapper orderMapper,
                        MerchantOrderMapper merchantOrderMapper, OrderItemMapper orderItemMapper,
                        OrderItemOptionMapper orderItemOptionMapper,
                        StringRedisTemplate redis, MetaProperties meta, ObjectMapper objectMapper,
                        MailOutboxWriter mailOutboxWriter) {
        this.checkoutService = checkoutService;
        this.skuMapper = skuMapper;
        this.cartItemMapper = cartItemMapper;
        this.userCouponMapper = userCouponMapper;
        this.orderMapper = orderMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderItemOptionMapper = orderItemOptionMapper;
        this.redis = redis;
        this.meta = meta;
        this.objectMapper = objectMapper;
        this.mailOutboxWriter = mailOutboxWriter;
    }

    @Transactional
    public OrderCreatedResult create(CreateOrderRequest request, String idempotencyKey) {
        Long userId = currentUserId();
        String idemKey = null;

        if (StringUtils.hasText(idempotencyKey)) {
            idemKey = IDEM_PREFIX + userId + ":" + idempotencyKey;
            String existingOrderNo = redis.opsForValue().get(idemKey);
            if (existingOrderNo != null) {
                return replay(userId, existingOrderNo);
            }
        }

        List<CheckoutRequest.Item> checkoutItems = request.items() == null ? null
                : request.items().stream()
                        .map(item -> new CheckoutRequest.Item(item.skuId(), item.quantity(),
                                item.options() == null ? null : item.options().stream()
                                        .map(option -> new CheckoutRequest.OptionValue(option.optionId(), option.value()))
                                        .toList()))
                        .toList();
        CheckoutPreview preview = checkoutService.preview(new CheckoutRequest(
                request.addressId(), request.couponId(), checkoutItems));

        boolean requiresFlawAck = preview.subOrders().stream()
                .flatMap(sub -> sub.items().stream())
                .anyMatch(CheckoutPreview.Line::requiresFlawAck);
        if (requiresFlawAck && !Boolean.TRUE.equals(request.flawAck())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请先确认商品的手作痕迹说明");
        }

        for (CheckoutPreview.SubOrder sub : preview.subOrders()) {
            for (CheckoutPreview.Line line : sub.items()) {
                int locked = skuMapper.lockStock(line.skuId(), line.quantity());
                if (locked == 0) {
                    throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                            line.name() + " 库存不足", java.util.Map.of("skuId", line.skuId()));
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(nextOrderNo());
        order.setUserId(userId);
        order.setStatus("PENDING_PAYMENT");
        order.setMerchantCount(preview.subOrders().size());
        order.setGoodsAmountCent(preview.summary().goodsAmount());
        order.setDiscountCent(preview.summary().discount());
        order.setShippingFeeCent(preview.summary().shippingFee());
        order.setPayAmountCent(preview.summary().payAmount());
        order.setAddressJson(writeJson(preview.address()));
        order.setCouponId(request.couponId());
        order.setRemark(request.remark());
        order.setOrderType(preview.summary().orderType());
        order.setDepositCent(preview.summary().deposit());
        order.setBalanceCent(preview.summary().balance());
        order.setPenaltyCent(0);
        order.setIsGift(Boolean.TRUE.equals(request.isGift()) ? 1 : 0);
        order.setGiftMessage(request.giftMessage());
        order.setGiftWrap(Boolean.TRUE.equals(request.giftWrap()) ? 1 : 0);
        order.setVersion(0);
        orderMapper.insert(order);

        List<OrderCreatedResult.SubOrder> created = new ArrayList<>();
        for (CheckoutPreview.SubOrder sub : preview.subOrders()) {
            MerchantOrder mo = new MerchantOrder();
            mo.setSubOrderNo(order.getOrderNo() + "-" + sub.merchantId());
            mo.setOrderId(order.getId());
            mo.setMerchantId(sub.merchantId());
            mo.setStatus("PENDING_PAYMENT");
            mo.setGoodsAmountCent(sub.goodsAmount());
            mo.setDiscountCent(sub.discount());
            mo.setShippingFeeCent(sub.shippingFee());
            mo.setPayAmountCent(sub.payAmount());
            mo.setRefundedAmountCent(0);
            mo.setSettled(0);
            merchantOrderMapper.insert(mo);

            for (CheckoutPreview.Line line : sub.items()) {
                OrderItem item = new OrderItem();
                item.setMerchantOrderId(mo.getId());
                item.setOrderId(order.getId());
                item.setMerchantId(sub.merchantId());
                item.setSkuId(line.skuId());
                item.setProductId(line.productId());
                item.setProductName(line.name());
                item.setProductImage(line.image());
                item.setShopName(sub.shopName());
                item.setSkuSpec(writeJson(line.spec()));
                item.setUnitPriceCent(line.price());
                item.setQuantity(line.quantity());
                item.setSubtotalCent(line.subtotal());
                item.setFlawAck(line.requiresFlawAck() ? 1 : 0);
                orderItemMapper.insert(item);
                for (CheckoutPreview.OptionSelection option : line.options()) {
                    OrderItemOption snapshot = new OrderItemOption();
                    snapshot.setOrderItemId(item.getId());
                    snapshot.setOptionId(option.optionId());
                    snapshot.setOptionName(option.name());
                    snapshot.setValueText(option.value());
                    snapshot.setPriceDeltaCent(option.priceDelta());
                    orderItemOptionMapper.insert(snapshot);
                }
            }

            created.add(new OrderCreatedResult.SubOrder(mo.getSubOrderNo(), sub.merchantId(),
                    sub.shopName(), sub.payAmount()));
        }

        if (request.couponId() != null) {
            int updated = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .eq(UserCoupon::getUserId, userId)
                    .eq(UserCoupon::getCouponId, request.couponId())
                    .eq(UserCoupon::getStatus, "UNUSED")
                    .set(UserCoupon::getStatus, "USED")
                    .set(UserCoupon::getOrderId, order.getId())
                    .set(UserCoupon::getUsedAt, now));
            if (updated == 0) {
                throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券已被使用");
            }
        }

        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, 1));

        if (idemKey != null) {
            redis.opsForValue().set(idemKey, order.getOrderNo(), IDEM_TTL);
        }

        boolean preorder = isPreorder(order);
        LocalDateTime expireAt = preorder
                ? now.plusHours(meta.preorderPayTimeoutHours())
                : now.plusMinutes(meta.payTimeoutMinutes());
        int dueNow = preorder ? order.getDepositCent() : order.getPayAmountCent();
        List<String> itemLines = preview.subOrders().stream()
                .flatMap(sub -> sub.items().stream())
                .map(line -> line.name() + " × " + line.quantity())
                .toList();
        mailOutboxWriter.enqueueToUser(userId, MailTemplateType.ORDER_CREATED,
                "order_created:" + order.getOrderNo(),
                Map.of(
                        "orderNo", order.getOrderNo(),
                        "items", itemLines,
                        "payAmount", BigDecimal.valueOf(dueNow, 2).toPlainString(),
                        "payUrl", "/orders/" + order.getOrderNo(),
                        "payDeadline", expireAt.format(MAIL_TIME)));
        return new OrderCreatedResult(order.getOrderNo(), order.getStatus(), dueNow, expireAt, created);
    }

    private OrderCreatedResult replay(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        List<MerchantOrder> subs = merchantOrderMapper.selectList(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getOrderId, order.getId()));
        List<OrderCreatedResult.SubOrder> created = subs.stream()
                .map(mo -> new OrderCreatedResult.SubOrder(mo.getSubOrderNo(), mo.getMerchantId(), null,
                        mo.getPayAmountCent()))
                .toList();
        boolean preorder = isPreorder(order);
        LocalDateTime expireAt = preorder
                ? order.getCreatedAt().plusHours(meta.preorderPayTimeoutHours())
                : order.getCreatedAt().plusMinutes(meta.payTimeoutMinutes());
        int dueNow = preorder ? order.getDepositCent() : order.getPayAmountCent();
        return new OrderCreatedResult(order.getOrderNo(), order.getStatus(), dueNow, expireAt, created);
    }

    private boolean isPreorder(Order order) {
        return "PREORDER".equals(order.getOrderType()) || "CUSTOM".equals(order.getOrderType());
    }

    private String nextOrderNo() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long seq = redis.opsForValue().increment(SEQ_PREFIX + day);
        return "KD" + day + String.format("%04d", seq == null ? 1 : seq % 10000);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "快照序列化失败");
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
