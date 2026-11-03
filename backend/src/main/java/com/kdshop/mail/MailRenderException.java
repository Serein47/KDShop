package com.kdshop.mail;

/** 模板缺失、变量缺失或模板不存在：不可重试，直接标 FAILED。 */
public class MailRenderException extends RuntimeException {

    public MailRenderException(String message) {
        super(message);
    }

    public MailRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
