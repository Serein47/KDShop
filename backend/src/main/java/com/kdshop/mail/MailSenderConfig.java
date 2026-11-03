package com.kdshop.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Locale;

/**
 * 按 kdshop.mail.mode 选发信实现：
 * SMTP 用于本地 Mailpit；OUTBOX_ONLY 是生产默认，只写 outbox 不真发。
 */
@Configuration
public class MailSenderConfig {

    @Bean
    public MailSender deliveryMailSender(JavaMailSender javaMailSender, MailProperties properties) {
        String mode = properties.mode() == null ? "OUTBOX_ONLY" : properties.mode().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "SMTP" -> new SmtpMailSender(javaMailSender, properties);
            case "OUTBOX_ONLY" -> new OutboxOnlySender();
            default -> throw new IllegalStateException("未知邮件模式：" + properties.mode()
                    + "，只支持 SMTP / OUTBOX_ONLY");
        };
    }
}
