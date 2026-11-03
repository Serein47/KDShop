package com.kdshop.mail;

/** 发信抽象：SMTP 与 OutboxOnly 两种实现。 */
public interface MailSender {

    void send(String to, String subject, String html, String text);
}
