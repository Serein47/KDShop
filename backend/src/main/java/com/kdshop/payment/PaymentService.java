package com.kdshop.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.MetaProperties;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.payment.dto.PaymentView;
import com.kdshop.payment.entity.Payment;
import com.kdshop.payment.mapper.PaymentMapper;
import com.kdshop.order.OrderPaidService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟支付网关：创建交易、支付页结果、签名回调。
 * 回调只推进支付单状态；订单状态与库存扣减在 ISSUE-13 接入。
 */
@Service
public class PaymentService {

    private static final Duration REPLAY_WINDOW = Duration.ofMinutes(5);
    private static final Duration NONCE_TTL = Duration.ofMinutes(10);

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final PaymentSigner signer;
    private final StringRedisTemplate redis;
    private final MetaProperties meta;
    private final ObjectMapper objectMapper;
    private final OrderPaidService orderPaidService;

    public PaymentService(PaymentMapper paymentMapper, OrderMapper orderMapper, PaymentSigner signer,
                          StringRedisTemplate redis, MetaProperties meta, ObjectMapper objectMapper,
                          OrderPaidService orderPaidService) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.signer = signer;
        this.redis = redis;
        this.meta = meta;
        this.objectMapper = objectMapper;
        this.orderPaidService = orderPaidService;
    }

    @Transactional
    public PaymentView create(String orderNo) {
        Long userId = currentUserId();
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单当前状态不可支付");
        }
        LocalDateTime expireAt = order.getCreatedAt().plusMinutes(meta.payTimeoutMinutes());
        if (LocalDateTime.now().isAfter(expireAt)) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单已超时，请重新下单");
        }

        Payment existing = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId())
                .eq(Payment::getStatus, "PENDING"));
        if (existing != null) {
            return toView(existing, expireAt);
        }

        Payment payment = new Payment();
        String paymentNo = "PAY" + System.currentTimeMillis()
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        payment.setPaymentNo(paymentNo);
        payment.setOutTradeNo(paymentNo);
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setChannel("MOCK");
        payment.setAmountCent(order.getPayAmountCent());
        payment.setStatus("PENDING");
        payment.setPurpose("FULL");
        paymentMapper.insert(payment);
        return toView(payment, expireAt);
    }

    public PaymentView status(String paymentNo) {
        Long userId = currentUserId();
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentNo, paymentNo));
        if (payment == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        return toView(payment, order.getCreatedAt().plusMinutes(meta.payTimeoutMinutes()));
    }

    /** 模拟网关页面上的"支付成功 / 失败"：由服务端签名后走同一套回调。 */
    @Transactional
    public PaymentView completeOnMockPage(String paymentNo, boolean success) {
        Long userId = currentUserId();
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentNo, paymentNo));
        if (payment == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("outTradeNo", payment.getOutTradeNo());
        payload.put("amount", String.valueOf(payment.getAmountCent()));
        payload.put("status", success ? "SUCCESS" : "FAILED");
        payload.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));
        payload.put("nonce", Long.toHexString(ThreadLocalRandom.current().nextLong()));
        payload.put("sign", signer.sign(payload));
        handleCallback(payload);
        return status(paymentNo);
    }

    @Transactional
    public Map<String, Object> handleCallback(Map<String, String> payload) {
        signer.verify(payload);

        long timestamp = Long.parseLong(payload.getOrDefault("timestamp", "0"));
        if (Math.abs(Instant.now().toEpochMilli() - timestamp) > REPLAY_WINDOW.toMillis()) {
            throw new BusinessException(ErrorCode.SIGNATURE_INVALID, "回调时间戳超出窗口");
        }
        String nonce = payload.get("nonce");
        Boolean fresh = redis.opsForValue().setIfAbsent("pay:nonce:" + nonce, "1", NONCE_TTL);
        if (!Boolean.TRUE.equals(fresh)) {
            throw new BusinessException(ErrorCode.SIGNATURE_INVALID, "回调重复");
        }

        String outTradeNo = payload.get("outTradeNo");
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOutTradeNo, outTradeNo));
        if (payment == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        int amount = Integer.parseInt(payload.getOrDefault("amount", "-1"));
        if (amount != payment.getAmountCent()) {
            throw new BusinessException(ErrorCode.AMOUNT_MISMATCH);
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            return Map.of("code", 0, "message", "ok", "idempotent", true);
        }
        // 订单取消会把待支付单置为 CLOSED；晚到的回调只确认收到，不再复活支付单
        if (!"PENDING".equals(payment.getStatus())) {
            return Map.of("code", 0, "message", "ok", "closed", true);
        }

        String result = payload.getOrDefault("status", "FAILED");
        Payment patch = new Payment();
        patch.setId(payment.getId());
        patch.setStatus("SUCCESS".equals(result) ? "SUCCESS" : "FAILED");
        patch.setCallbackAt(LocalDateTime.now());
        try {
            patch.setCallbackRaw(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            patch.setCallbackRaw("{}");
        }
        if ("SUCCESS".equals(patch.getStatus())) {
            patch.setPaidAt(LocalDateTime.now());
        }
        paymentMapper.updateById(patch);
        if ("SUCCESS".equals(patch.getStatus())) {
            orderPaidService.markPaid(payment.getOrderId(), payment.getPaymentNo());
        }
        return Map.of("code", 0, "message", "ok");
    }

    private PaymentView toView(Payment payment, LocalDateTime expireAt) {
        return new PaymentView(payment.getPaymentNo(), payment.getOrderNo(), payment.getAmountCent(),
                payment.getStatus(), "/mock-pay/" + payment.getPaymentNo(), expireAt, payment.getPaidAt());
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
