package com.kdshop.mail;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MailSenderConfigTest {

    private final MailSenderConfig config = new MailSenderConfig();
    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);

    @Test
    void outboxOnlyModeNeverCreatesSmtpSender() {
        MailProperties properties = new MailProperties(
                "OUTBOX_ONLY", "no-reply@kdshop.local", "KDShop", "https://shop.test", 3, 60, 20);

        assertThat(config.deliveryMailSender(javaMailSender, properties))
                .isInstanceOf(OutboxOnlySender.class);
    }

    @Test
    void smtpModeCreatesSmtpSenderForLocalMailpit() {
        MailProperties properties = new MailProperties(
                "SMTP", "no-reply@kdshop.local", "KDShop", "https://shop.test", 3, 60, 20);

        assertThat(config.deliveryMailSender(javaMailSender, properties))
                .isInstanceOf(SmtpMailSender.class);
    }
}
