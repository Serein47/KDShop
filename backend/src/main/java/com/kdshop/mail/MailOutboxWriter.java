package com.kdshop.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务代码只负责入队，不直接发信。
 * 入队失败只记日志，不影响下单、注册等主流程；biz_key 唯一索引保证幂等。
 */
@Slf4j
@Service
public class MailOutboxWriter {

    private final EmailOutboxMapper emailOutboxMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final MailProperties properties;

    public MailOutboxWriter(EmailOutboxMapper emailOutboxMapper, UserMapper userMapper,
                            ObjectMapper objectMapper, MailProperties properties) {
        this.emailOutboxMapper = emailOutboxMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueToUser(Long userId, MailTemplateType type, String bizKey, Map<String, Object> payload) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                log.warn("邮件入队跳过：找不到收件人 userId={}, template={}", userId, type);
                return;
            }
            doEnqueue(user.getEmail(), type, bizKey, payload);
        } catch (Exception e) {
            log.error("邮件入队失败，业务继续：userId={}, template={}", userId, type, e);
        }
    }

    /**
     * 使用 REQUIRES_NEW 隔离：邮件表异常不回滚业务事务。
     * 失败只记日志，业务接口照常成功。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(String to, MailTemplateType type, String bizKey, Map<String, Object> payload) {
        doEnqueue(to, type, bizKey, payload);
    }

    private void doEnqueue(String to, MailTemplateType type, String bizKey, Map<String, Object> payload) {
        EmailOutbox box = new EmailOutbox();
        box.setToEmail(to);
        box.setTemplate(type.name());
        box.setBizKey(bizKey);
        box.setStatus("PENDING");
        box.setAttempts(0);
        try {
            box.setPayloadJson(objectMapper.writeValueAsString(withAbsoluteUrls(payload)));
        } catch (Exception e) {
            log.error("邮件变量序列化失败，跳过入队：bizKey={}, template={}", bizKey, type, e);
            return;
        }
        try {
            emailOutboxMapper.insert(box);
            log.debug("邮件已入队：to={}, template={}, bizKey={}", to, type, bizKey);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.info("邮件已存在，跳过重复入队：bizKey={}", bizKey);
        } catch (Exception e) {
            log.error("邮件入队失败，业务继续：bizKey={}, template={}", bizKey, type, e);
        }
    }

    private Map<String, Object> withAbsoluteUrls(Map<String, Object> payload) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        String base = properties.baseUrl();
        if (base == null || base.isBlank()) {
            return copy;
        }
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        copy.replaceAll((key, value) -> {
            if (key.endsWith("Url") && value instanceof String url && url.startsWith("/")) {
                return normalizedBase + url;
            }
            return value;
        });
        return copy;
    }
}
