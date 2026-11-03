package com.kdshop.mail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 轻量模板渲染：{{key}} 占位符，HTML 自动转义，纯文本保留原样。
 * 模板文件位于 classpath:mail-templates/{CODE}.html 与 {CODE}.txt。
 */
@Service
public class MailTemplateService {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private final ObjectMapper objectMapper;

    public MailTemplateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RenderedMail render(MailTemplateType type, String payloadJson) {
        Map<String, Object> payload = readPayload(payloadJson);
        String subject = fill(type.subject(), payload, false);
        String html = fill(resource(type.name() + ".html"), payload, true);
        String text = fill(resource(type.name() + ".txt"), payload, false);
        return new RenderedMail(subject, html, text);
    }

    private Map<String, Object> readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new MailRenderException("邮件变量不是合法 JSON", e);
        }
    }

    private String resource(String name) {
        try (InputStream input = new ClassPathResource("mail-templates/" + name).getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MailRenderException("缺少模板文件：" + name, e);
        }
    }

    private String fill(String template, Map<String, Object> payload, boolean html) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = payload.get(key);
            if (value == null) {
                throw new MailRenderException("模板变量缺失：" + key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(formatValue(value, html)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String formatValue(Object value, boolean html) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> html ? escapeHtml(String.valueOf(item)) : String.valueOf(item))
                    .collect(Collectors.joining(html ? "<br/>" : "\n"));
        }
        String text = String.valueOf(value);
        return html ? escapeHtml(text) : text;
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record RenderedMail(String subject, String html, String text) {
    }
}
