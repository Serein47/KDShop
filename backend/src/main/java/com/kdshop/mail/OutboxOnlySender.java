package com.kdshop.mail;

import lombok.extern.slf4j.Slf4j;

/** 生产演示：只把邮件留在 outbox，不触达真实邮箱。 */
@Slf4j
public class OutboxOnlySender implements MailSender {

    @Override
    public void send(String to, String subject, String html, String text) {
        log.info("OutboxOnly：跳过真实投递，to={}, subject={}", to, subject);
    }
}
