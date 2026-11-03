package com.kdshop.mail;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.kdshop.mail.entity.EmailOutbox;
import com.kdshop.mail.mapper.EmailOutboxMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxDispatcherTest {

    @Mock private EmailOutboxMapper emailOutboxMapper;
    @Mock private MailTemplateService templateService;
    @Mock private MailSender mailSender;

    private final MailProperties properties = new MailProperties(
            "SMTP", "no-reply@kdshop.local", "KDShop", "https://shop.test", 3, 60, 20);
    private MailOutboxDispatcher dispatcher;

    @BeforeAll
    static void initMybatisLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EmailOutbox.class);
    }

    @BeforeEach
    void setUp() {
        dispatcher = new MailOutboxDispatcher(emailOutboxMapper, templateService, mailSender, properties);
    }

    @Test
    void successfulDeliveryMarksSent() {
        EmailOutbox box = pending(1L, 0);
        when(emailOutboxMapper.selectList(any())).thenReturn(List.of(box));
        when(templateService.render(any(), any()))
                .thenReturn(new MailTemplateService.RenderedMail("主题", "<p>html</p>", "text"));

        dispatcher.dispatch();

        verify(mailSender).send("customer@kdshop.dev", "主题", "<p>html</p>", "text");
        EmailOutbox patch = captureUpdate();
        assertThat(patch.getStatus()).isEqualTo("SENT");
        assertThat(patch.getAttempts()).isEqualTo(1);
        assertThat(patch.getSentAt()).isNotNull();
    }

    @Test
    void failedDeliverySchedulesRetry() {
        EmailOutbox box = pending(1L, 0);
        when(emailOutboxMapper.selectList(any())).thenReturn(List.of(box));
        when(templateService.render(any(), any()))
                .thenReturn(new MailTemplateService.RenderedMail("主题", "html", "text"));
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp down"))
                .when(mailSender).send(any(), any(), any(), any());

        dispatcher.dispatch();

        EmailOutbox patch = captureUpdate();
        assertThat(patch.getStatus()).isEqualTo("PENDING");
        assertThat(patch.getAttempts()).isEqualTo(1);
        assertThat(patch.getNextRetryAt()).isNotNull();
        assertThat(patch.getError()).contains("smtp down");
    }

    @Test
    void thirdFailureMarksFailed() {
        EmailOutbox box = pending(1L, 2);
        when(emailOutboxMapper.selectList(any())).thenReturn(List.of(box));
        when(templateService.render(any(), any()))
                .thenReturn(new MailTemplateService.RenderedMail("主题", "html", "text"));
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp down"))
                .when(mailSender).send(any(), any(), any(), any());

        dispatcher.dispatch();

        EmailOutbox patch = captureUpdate();
        assertThat(patch.getStatus()).isEqualTo("FAILED");
        assertThat(patch.getAttempts()).isEqualTo(3);
    }

    @Test
    void renderErrorMarksFailedWithoutSending() {
        EmailOutbox box = pending(1L, 0);
        when(emailOutboxMapper.selectList(any())).thenReturn(List.of(box));
        when(templateService.render(any(), any())).thenThrow(new MailRenderException("缺少 verifyUrl"));

        dispatcher.dispatch();

        EmailOutbox patch = captureUpdate();
        assertThat(patch.getStatus()).isEqualTo("FAILED");
        assertThat(patch.getError()).contains("verifyUrl");
        verify(mailSender, never()).send(any(), any(), any(), any());
    }

    private EmailOutbox pending(Long id, int attempts) {
        EmailOutbox box = new EmailOutbox();
        box.setId(id);
        box.setToEmail("customer@kdshop.dev");
        box.setTemplate("EMAIL_VERIFY");
        box.setBizKey("verify:1:1");
        box.setPayloadJson("{}");
        box.setStatus("PENDING");
        box.setAttempts(attempts);
        return box;
    }

    private EmailOutbox captureUpdate() {
        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(emailOutboxMapper).updateById(captor.capture());
        return captor.getValue();
    }
}
