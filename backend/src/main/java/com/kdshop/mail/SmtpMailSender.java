package com.kdshop.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

/** 本地开发：把 HTML + 纯文本邮件投递到 Mailpit（SMTP 1025）。 */
@Slf4j
public class SmtpMailSender implements MailSender {

    private final JavaMailSender javaMailSender;
    private final MailProperties properties;

    public SmtpMailSender(JavaMailSender javaMailSender, MailProperties properties) {
        this.javaMailSender = javaMailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String html, String text) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.from(), properties.fromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            javaMailSender.send(message);
            log.info("邮件已投递到 SMTP：to={}, subject={}", to, subject);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("构造邮件失败", e);
        }
    }
}
