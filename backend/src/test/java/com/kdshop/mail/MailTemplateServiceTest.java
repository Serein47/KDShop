package com.kdshop.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailTemplateServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MailTemplateService service = new MailTemplateService(objectMapper);

    @Test
    void rendersVerificationTemplateWithHtmlAndText() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "nickname", "张三",
                "verifyUrl", "http://localhost:5173/verify-email?token=abc",
                "expireMinutes", 30));

        var mail = service.render(MailTemplateType.EMAIL_VERIFY, payload);

        assertThat(mail.subject()).isEqualTo("请验证你的 KDShop 邮箱");
        assertThat(mail.html()).contains("张三").contains("verify-email?token=abc");
        assertThat(mail.text()).contains("30 分钟内有效");
    }

    @Test
    void rendersOrderCreatedItemsAsHtmlBreaksAndTextLines() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "orderNo", "KD202611030001",
                "items", List.of("粗陶马克杯 × 2", "亚麻餐垫 × 1"),
                "payAmount", "128.00",
                "payUrl", "http://localhost:5173/orders/KD202611030001",
                "payDeadline", "2026-11-03 18:00"));

        var mail = service.render(MailTemplateType.ORDER_CREATED, payload);

        assertThat(mail.subject()).contains("KD202611030001").contains("2026-11-03 18:00");
        assertThat(mail.html()).contains("粗陶马克杯 × 2<br/>亚麻餐垫 × 1");
        assertThat(mail.text()).contains("粗陶马克杯 × 2\n亚麻餐垫 × 1");
    }

    @Test
    void rendersPaymentAndShippingTemplates() throws Exception {
        var payment = service.render(MailTemplateType.PAYMENT_SUCCESS,
                objectMapper.writeValueAsString(Map.of(
                        "orderNo", "KD202611030001",
                        "paidAmount", "128.00",
                        "paidAt", "2026-11-03 17:30",
                        "orderUrl", "http://localhost:5173/orders/KD202611030001")));
        assertThat(payment.html()).contains("实付 ¥128.00").contains("查看订单");

        var shipped = service.render(MailTemplateType.ORDER_SHIPPED,
                objectMapper.writeValueAsString(Map.of(
                        "orderNo", "KD202611030001",
                        "carrier", "顺丰",
                        "trackingNo", "SF1234567890",
                        "shippedAt", "2026-11-04 09:00",
                        "orderUrl", "http://localhost:5173/orders/KD202611030001")));
        assertThat(shipped.html()).contains("顺丰").contains("SF1234567890");
        assertThat(shipped.text()).contains("运单号：SF1234567890");
    }

    @Test
    void rendersPasswordResetTemplate() throws Exception {
        var mail = service.render(MailTemplateType.PASSWORD_RESET,
                objectMapper.writeValueAsString(Map.of(
                        "nickname", "张三",
                        "resetUrl", "http://localhost:5173/reset-password?token=abc",
                        "expireMinutes", 30)));

        assertThat(mail.subject()).isEqualTo("重置你的 KDShop 密码");
        assertThat(mail.html()).contains("reset-password?token=abc");
        assertThat(mail.text()).contains("30 分钟内有效");
    }

    @Test
    void missingVariableFailsWithoutRetry() {
        assertThatThrownBy(() -> service.render(MailTemplateType.EMAIL_VERIFY, "{\"nickname\":\"张三\"}"))
                .isInstanceOf(MailRenderException.class)
                .hasMessageContaining("verifyUrl");
    }
}
