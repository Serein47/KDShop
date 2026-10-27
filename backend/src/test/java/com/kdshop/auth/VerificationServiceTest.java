package com.kdshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.entity.AuthToken;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.AuthTokenMapper;
import com.kdshop.auth.mapper.EmailOutboxMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.SessionRepository;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private AuthTokenMapper authTokenMapper;
    @Mock private EmailOutboxMapper emailOutboxMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redis;
    @Mock private SetOperations<String, String> setOps;
    @Mock private SessionRepository<?> sessionRepository;
    @Mock private HttpServletRequest request;
    @Mock private HttpSession session;

    private VerificationService service;

    @BeforeEach
    void setUp() {
        service = new VerificationService(userMapper, authTokenMapper, emailOutboxMapper,
                passwordEncoder, redis, sessionRepository, new ObjectMapper());
    }

    @Test
    void usedTokenIsRejected() {
        AuthToken token = new AuthToken();
        token.setUsedAt(LocalDateTime.now().minusMinutes(1));
        when(authTokenMapper.selectOne(any())).thenReturn(token);

        assertThatThrownBy(() -> service.verifyEmail("abc"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void expiredTokenIsRejectedWithHint() {
        AuthToken token = new AuthToken();
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(authTokenMapper.selectOne(any())).thenReturn(token);

        assertThatThrownBy(() -> service.resetPassword("abc", "Passw0rd1", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过期");
    }

    @Test
    void resetInvalidatesOtherSessionsAndCurrentOne() {
        AuthToken token = new AuthToken();
        token.setUserId(7L);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(authTokenMapper.selectOne(any())).thenReturn(token);
        when(passwordEncoder.encode("Passw0rd1")).thenReturn("hash");
        when(request.getSession(false)).thenReturn(session);
        when(session.getId()).thenReturn("current");
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.members("user:sessions:7")).thenReturn(Set.of("current", "other"));
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        service.resetPassword("abc", "Passw0rd1", request);

        verify(sessionRepository).deleteById("other");
        verify(sessionRepository, never()).deleteById(eq("current"));
        verify(session).invalidate();
        verify(redis).delete(anyString());
    }
}
