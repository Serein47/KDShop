package com.kdshop.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.auth.entity.AuthToken;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.AuthTokenMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.mail.MailOutboxWriter;
import com.kdshop.mail.MailTemplateType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 邮箱验证与密码重置：令牌一次性、30 分钟有效，密码重置后踢掉该用户全部会话。 */
@Service
public class VerificationService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final String RESEND_KEY = "rate:resend:";
    private static final String SESSION_KEY = "user:sessions:";

    private final UserMapper userMapper;
    private final AuthTokenMapper authTokenMapper;
    private final MailOutboxWriter mailOutboxWriter;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final SessionRepository<?> sessionRepository;

    public VerificationService(UserMapper userMapper, AuthTokenMapper authTokenMapper,
                               MailOutboxWriter mailOutboxWriter, PasswordEncoder passwordEncoder,
                               StringRedisTemplate redis, SessionRepository<?> sessionRepository) {
        this.userMapper = userMapper;
        this.authTokenMapper = authTokenMapper;
        this.mailOutboxWriter = mailOutboxWriter;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AuthToken token = requireToken(rawToken, "VERIFY");
        token.setUsedAt(LocalDateTime.now());
        authTokenMapper.updateById(token);

        User user = userMapper.selectById(token.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        User patch = new User();
        patch.setId(user.getId());
        patch.setEmailVerifiedAt(LocalDateTime.now());
        userMapper.updateById(patch);
    }

    /** 恒返回成功，避免暴露邮箱是否注册；60 秒内重复请求不再发信。 */
    public boolean resendVerification(String email) {
        String normalized = normalize(email);
        if (!acquireResendSlot(normalized)) {
            return true;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, normalized));
        if (user == null || user.getEmailVerifiedAt() != null) {
            return true;
        }
        issueToken(user, "VERIFY", MailTemplateType.EMAIL_VERIFY, "verify",
                "/verify-email?token=", Map.of("nickname", user.getNickname()));
        return true;
    }

    /** 同样恒返回成功，防止用重置接口探测邮箱是否存在。 */
    public boolean forgotPassword(String email) {
        String normalized = normalize(email);
        if (!acquireResendSlot(normalized)) {
            return true;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, normalized));
        if (user == null) {
            return true;
        }
        issueToken(user, "RESET", MailTemplateType.PASSWORD_RESET, "reset",
                "/reset-password?token=", Map.of("nickname", user.getNickname()));
        return true;
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword, HttpServletRequest request) {
        AuthToken token = requireToken(rawToken, "RESET");
        token.setUsedAt(LocalDateTime.now());
        authTokenMapper.updateById(token);

        User patch = new User();
        patch.setId(token.getUserId());
        patch.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(patch);

        invalidateSessions(token.getUserId(), request);
    }

    private void invalidateSessions(Long userId, HttpServletRequest request) {
        HttpSession current = request.getSession(false);
        String currentId = current == null ? null : current.getId();

        String key = SESSION_KEY + userId;
        Set<String> sessionIds = redis.opsForSet().members(key);
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                if (!sessionId.equals(currentId)) {
                    sessionRepository.deleteById(sessionId);
                }
            }
        }
        redis.delete(key);

        // 当前请求的会话交给容器自身失效，否则提交阶段会再次保存已删除的会话
        if (current != null) {
            current.invalidate();
        }
    }

    private AuthToken requireToken(String rawToken, String type) {
        AuthToken token = authTokenMapper.selectOne(new LambdaQueryWrapper<AuthToken>()
                .eq(AuthToken::getTokenHash, Tokens.sha256(rawToken))
                .eq(AuthToken::getType, type));
        if (token == null || token.getUsedAt() != null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "链接已过期，请重新获取");
        }
        return token;
    }

    private boolean acquireResendSlot(String email) {
        Boolean acquired = redis.opsForValue().setIfAbsent(RESEND_KEY + email, "1", RESEND_INTERVAL);
        return Boolean.TRUE.equals(acquired);
    }

    private void issueToken(User user, String type, MailTemplateType template, String bizPrefix,
                            String urlPrefix, Map<String, Object> extra) {
        String raw = Tokens.random();
        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        token.setType(type);
        token.setTokenHash(Tokens.sha256(raw));
        token.setExpiresAt(LocalDateTime.now().plus(TOKEN_TTL));
        authTokenMapper.insert(token);

        String urlKey = "VERIFY".equals(type) ? "verifyUrl" : "resetUrl";
        Map<String, Object> payload = new HashMap<>(extra);
        payload.put(urlKey, urlPrefix + raw);
        payload.put("expireMinutes", TOKEN_TTL.toMinutes());

        mailOutboxWriter.enqueue(user.getEmail(), template,
                bizPrefix + ":" + user.getId() + ":" + token.getId(), payload);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
