package com.kdshop.mail;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台投递 outbox：只处理 PENDING，按 attempts 做最多 N 次重试。
 * 单实例演示足够；多实例部署时需要先把行原子标记为 SENDING 再发。
 */
@Slf4j
@Component
public class MailOutboxDispatcher {

    private static final int MAX_ERROR_LENGTH = 500;

    private final EmailOutboxMapper emailOutboxMapper;
    private final MailTemplateService templateService;
    private final MailSender mailSender;
    private final MailProperties properties;

    public MailOutboxDispatcher(EmailOutboxMapper emailOutboxMapper, MailTemplateService templateService,
                                MailSender mailSender, MailProperties properties) {
        this.emailOutboxMapper = emailOutboxMapper;
        this.templateService = templateService;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${kdshop.mail.initial-delay-ms:3000}",
            fixedDelayString = "${kdshop.mail.dispatch-interval-ms:5000}")
    public void dispatch() {
        LocalDateTime now = LocalDateTime.now();
        List<EmailOutbox> batch;
        try {
            batch = emailOutboxMapper.selectList(new LambdaQueryWrapper<EmailOutbox>()
                    .eq(EmailOutbox::getStatus, "PENDING")
                    .and(w -> w.isNull(EmailOutbox::getNextRetryAt)
                            .or()
                            .le(EmailOutbox::getNextRetryAt, now))
                    .orderByAsc(EmailOutbox::getCreatedAt)
                    .last("LIMIT " + Math.max(1, properties.batchSize())));
        } catch (Exception e) {
            log.error("读取邮件 outbox 失败", e);
            return;
        }
        if (batch.isEmpty()) {
            return;
        }
        int sent = 0;
        int failed = 0;
        for (EmailOutbox box : batch) {
            try {
                if (deliver(box)) {
                    sent++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                log.error("邮件投递出现未预期异常：id={}, bizKey={}", box.getId(), box.getBizKey(), e);
            }
        }
        log.info("邮件 outbox 扫描：{} 条，成功 {}，失败 {}", batch.size(), sent, failed);
    }

    /** @return true 表示已投递或已标记失败，false 表示进入重试队列 */
    private boolean deliver(EmailOutbox box) {
        MailTemplateType type = MailTemplateType.fromCode(box.getTemplate()).orElse(null);
        if (type == null) {
            markFailed(box, "未知邮件模板：" + box.getTemplate());
            return false;
        }

        MailTemplateService.RenderedMail mail;
        try {
            mail = templateService.render(type, box.getPayloadJson());
        } catch (MailRenderException e) {
            markFailed(box, "模板渲染失败：" + e.getMessage());
            return false;
        }

        try {
            mailSender.send(box.getToEmail(), mail.subject(), mail.html(), mail.text());
            markSent(box);
            return true;
        } catch (Exception e) {
            markRetry(box, e);
            return false;
        }
    }

    private void markSent(EmailOutbox box) {
        EmailOutbox patch = new EmailOutbox();
        patch.setId(box.getId());
        patch.setStatus("SENT");
        patch.setAttempts(attemptsOf(box) + 1);
        patch.setSentAt(LocalDateTime.now());
        patch.setError("");
        emailOutboxMapper.updateById(patch);
    }

    private void markFailed(EmailOutbox box, String error) {
        EmailOutbox patch = new EmailOutbox();
        patch.setId(box.getId());
        patch.setStatus("FAILED");
        patch.setAttempts(attemptsOf(box) + 1);
        patch.setError(truncate(error));
        emailOutboxMapper.updateById(patch);
    }

    private void markRetry(EmailOutbox box, Exception cause) {
        int attempts = attemptsOf(box) + 1;
        boolean exhausted = attempts >= Math.max(1, properties.maxAttempts());
        EmailOutbox patch = new EmailOutbox();
        patch.setId(box.getId());
        patch.setAttempts(attempts);
        patch.setError(truncate(cause.getMessage() == null
                ? cause.getClass().getSimpleName() : cause.getMessage()));
        if (exhausted) {
            patch.setStatus("FAILED");
            log.warn("邮件投递失败已达上限：id={}, attempts={}", box.getId(), attempts);
        } else {
            patch.setStatus("PENDING");
            patch.setNextRetryAt(LocalDateTime.now()
                    .plusSeconds((long) properties.retryDelaySeconds() * attempts));
            log.warn("邮件投递失败，{} 秒后重试：id={}, attempts={}",
                    (long) properties.retryDelaySeconds() * attempts, box.getId(), attempts);
        }
        emailOutboxMapper.updateById(patch);
    }

    private int attemptsOf(EmailOutbox box) {
        return box.getAttempts() == null ? 0 : box.getAttempts();
    }

    private String truncate(String error) {
        if (error == null) {
            return "未知错误";
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
