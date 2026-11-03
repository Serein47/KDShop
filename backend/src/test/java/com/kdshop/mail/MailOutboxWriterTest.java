package com.kdshop.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxWriterTest {

    @Mock private EmailOutboxMapper emailOutboxMapper;
    @Mock private UserMapper userMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MailProperties properties = new MailProperties(
            "SMTP", "no-reply@kdshop.local", "KDShop", "https://shop.test", 3, 60, 20);
    private MailOutboxWriter writer;

    @BeforeEach
    void setUp() {
        writer = new MailOutboxWriter(emailOutboxMapper, userMapper, objectMapper, properties);
    }

    @Test
    void enqueueWritesPendingRowAndMakesUrlAbsolute() throws Exception {
        writer.enqueue("customer@kdshop.dev", MailTemplateType.ORDER_CREATED,
                "order_created:KD1", Map.of("payUrl", "/orders/KD1"));

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(emailOutboxMapper).insert(captor.capture());
        EmailOutbox box = captor.getValue();

        assertThat(box.getToEmail()).isEqualTo("customer@kdshop.dev");
        assertThat(box.getTemplate()).isEqualTo("ORDER_CREATED");
        assertThat(box.getBizKey()).isEqualTo("order_created:KD1");
        assertThat(box.getStatus()).isEqualTo("PENDING");
        assertThat(box.getAttempts()).isZero();
        assertThat(box.getPayloadJson()).contains("https://shop.test/orders/KD1");
    }

    @Test
    void duplicateBusinessKeyIsSwallowed() {
        when(emailOutboxMapper.insert(any(EmailOutbox.class)))
                .thenThrow(new DuplicateKeyException("uk_outbox_biz_key"));

        assertThatCode(() -> writer.enqueue("customer@kdshop.dev", MailTemplateType.PAYMENT_SUCCESS,
                "payment_success:PAY1", Map.of("orderNo", "KD1")))
                .doesNotThrowAnyException();
    }

    @Test
    void insertFailureDoesNotBreakBusinessFlow() {
        when(emailOutboxMapper.insert(any(EmailOutbox.class)))
                .thenThrow(new IllegalStateException("email_outbox is read-only"));

        assertThatCode(() -> writer.enqueue("customer@kdshop.dev", MailTemplateType.ORDER_CREATED,
                "order_created:KD1", Map.of("orderNo", "KD1")))
                .doesNotThrowAnyException();
    }

    @Test
    void enqueueToUserResolvesEmail() {
        User user = new User();
        user.setId(1L);
        user.setEmail("customer@kdshop.dev");
        when(userMapper.selectById(1L)).thenReturn(user);

        writer.enqueueToUser(1L, MailTemplateType.PAYMENT_SUCCESS, "payment_success:PAY1",
                Map.of("orderNo", "KD1"));

        verify(emailOutboxMapper).insert(any(EmailOutbox.class));
    }

    @Test
    void missingUserOnlyLogs() {
        when(userMapper.selectById(9L)).thenReturn(null);

        writer.enqueueToUser(9L, MailTemplateType.PAYMENT_SUCCESS, "payment_success:PAY9",
                Map.of("orderNo", "KD9"));

        verify(emailOutboxMapper, never()).insert(any(EmailOutbox.class));
    }
}
