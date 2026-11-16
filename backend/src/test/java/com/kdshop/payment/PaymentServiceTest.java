package com.kdshop.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.MetaProperties;
import com.kdshop.order.OrderPaidService;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.payment.dto.PaymentView;
import com.kdshop.payment.entity.Payment;
import com.kdshop.payment.mapper.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentMapper paymentMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderPaidService orderPaidService;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private PaymentSigner signer;
    private PaymentService service;
    private final MetaProperties meta = new MetaProperties("CNY", "Asia/Shanghai", 9900, 1000, 15, 15, 7, 24, 3, 7);

    @BeforeEach
    void setUp() {
        signer = new PaymentSigner("test-secret");
        service = new PaymentService(paymentMapper, orderMapper, signer, redis, meta, new ObjectMapper(), orderPaidService);
    }

    @Test
    void tamperedPayloadIsRejected() {
        Map<String, String> payload = basePayload(100);
        payload.put("sign", signer.sign(payload));
        payload.put("amount", "1");

        assertThatThrownBy(() -> service.handleCallback(payload))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SIGNATURE_INVALID);
    }

    @Test
    void amountMismatchIsRejected() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOutTradeNo("PAY1");
        payment.setAmountCent(100);
        payment.setStatus("PENDING");
        when(paymentMapper.selectOne(any())).thenReturn(payment);

        Map<String, String> payload = basePayload(999);
        payload.put("sign", signer.sign(payload));

        assertThatThrownBy(() -> service.handleCallback(payload))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AMOUNT_MISMATCH);
    }

    @Test
    void replayedNonceIsRejected() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        Map<String, String> payload = basePayload(100);
        payload.put("sign", signer.sign(payload));

        assertThatThrownBy(() -> service.handleCallback(payload))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SIGNATURE_INVALID);
    }

    @Test
    void duplicateSuccessCallbackIsIdempotent() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOutTradeNo("PAY1");
        payment.setAmountCent(100);
        payment.setStatus("SUCCESS");
        when(paymentMapper.selectOne(any())).thenReturn(payment);

        Map<String, String> payload = basePayload(100);
        payload.put("sign", signer.sign(payload));

        Map<String, Object> result = service.handleCallback(payload);

        assertThat(result.get("idempotent")).isEqualTo(true);
        verify(paymentMapper, org.mockito.Mockito.never()).updateById(any(Payment.class));
    }

    @Test
    void lateCallbackForClosedPaymentIsIgnored() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOutTradeNo("PAY1");
        payment.setAmountCent(100);
        payment.setStatus("CLOSED");
        when(paymentMapper.selectOne(any())).thenReturn(payment);

        Map<String, String> payload = basePayload(100);
        payload.put("sign", signer.sign(payload));

        Map<String, Object> result = service.handleCallback(payload);

        assertThat(result.get("closed")).isEqualTo(true);
        verify(paymentMapper, org.mockito.Mockito.never()).updateById(any(Payment.class));
        verify(orderPaidService, org.mockito.Mockito.never()).markPaid(any(Payment.class));
    }

    @Test
    void preorderCreatesDepositPaymentOnly() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("KD202611160001");
        order.setUserId(1L);
        order.setStatus("PENDING_PAYMENT");
        order.setOrderType("CUSTOM");
        order.setPayAmountCent(16800);
        order.setDepositCent(5000);
        order.setBalanceCent(11800);
        order.setCreatedAt(LocalDateTime.now());
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(paymentMapper.selectOne(any())).thenReturn(null);

        PaymentView view = service.create("KD202611160001");

        assertThat(view.purpose()).isEqualTo("DEPOSIT");
        assertThat(view.amount()).isEqualTo(5000);
        SecurityContextHolder.clearContext();
    }

    @Test
    void producingOrderCreatesBalancePayment() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("KD202611160002");
        order.setUserId(1L);
        order.setStatus("PRODUCING");
        order.setOrderType("CUSTOM");
        order.setPayAmountCent(16800);
        order.setDepositCent(5000);
        order.setBalanceCent(11800);
        order.setBalanceDueAt(LocalDateTime.now().plusDays(5));
        order.setCreatedAt(LocalDateTime.now().minusDays(2));
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(paymentMapper.selectOne(any())).thenReturn(null);

        PaymentView view = service.create("KD202611160002");

        assertThat(view.purpose()).isEqualTo("BALANCE");
        assertThat(view.amount()).isEqualTo(11800);
        SecurityContextHolder.clearContext();
    }

    private Map<String, String> basePayload(int amount) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("outTradeNo", "PAY1");
        payload.put("amount", String.valueOf(amount));
        payload.put("status", "SUCCESS");
        payload.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));
        payload.put("nonce", "n-" + System.nanoTime());
        return payload;
    }
}
