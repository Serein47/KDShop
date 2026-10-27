package com.kdshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.dto.LoginRequest;
import com.kdshop.auth.dto.RegisterRequest;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.AuthTokenMapper;
import com.kdshop.auth.mapper.EmailOutboxMapper;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private AuthTokenMapper authTokenMapper;
    @Mock private EmailOutboxMapper emailOutboxMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SecurityContextRepository securityContextRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, roleMapper, userRoleMapper, authTokenMapper,
                emailOutboxMapper, passwordEncoder, redis, securityContextRepository, new ObjectMapper());
    }

    @Test
    void duplicateEmailIsRejected() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("dup@kdshop.dev", "Passw0rd1", "重复")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_USED);

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void lockedAccountIsRejectedBeforeTouchingDatabase() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rate:login:locked@kdshop.dev")).thenReturn("5");

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("locked@kdshop.dev", "whatever"), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);

        verify(userMapper, never()).selectOne(any());
    }

    @Test
    void wrongPasswordCountsFailureAndHidesReason() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("nobody@kdshop.dev", "whatever"), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.BAD_CREDENTIALS);

        verify(redis).expire(anyString(), any());
    }
}
