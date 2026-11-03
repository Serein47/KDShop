package com.kdshop.mail;

import java.util.Arrays;
import java.util.Optional;

/** 首版 5 类事务邮件：验证、重置密码、下单、支付成功、发货。 */
public enum MailTemplateType {

    EMAIL_VERIFY("请验证你的 KDShop 邮箱"),
    PASSWORD_RESET("重置你的 KDShop 密码"),
    ORDER_CREATED("订单 {{orderNo}} 已创建，请在 {{payDeadline}} 前完成支付"),
    PAYMENT_SUCCESS("支付成功，订单 {{orderNo}} 正在备货"),
    ORDER_SHIPPED("你的订单 {{orderNo}} 已发货");

    private final String subject;

    MailTemplateType(String subject) {
        this.subject = subject;
    }

    public String subject() {
        return subject;
    }

    public static Optional<MailTemplateType> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equals(code))
                .findFirst();
    }
}
