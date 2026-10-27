package com.kdshop.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.auth.dto.LoginRequest;
import com.kdshop.auth.dto.RegisterRequest;
import com.kdshop.auth.dto.RegisterResult;
import com.kdshop.auth.dto.UserView;
import com.kdshop.auth.entity.AuthToken;
import com.kdshop.auth.entity.EmailOutbox;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.AuthTokenMapper;
import com.kdshop.auth.mapper.EmailOutboxMapper;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration VERIFY_TOKEN_TTL = Duration.ofMinutes(30);
    private static final String LOGIN_KEY_PREFIX = "rate:login:";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthTokenMapper authTokenMapper;
    private final EmailOutboxMapper emailOutboxMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final SecurityContextRepository securityContextRepository;
    private final ObjectMapper objectMapper;

    public AuthService(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                       AuthTokenMapper authTokenMapper, EmailOutboxMapper emailOutboxMapper,
                       PasswordEncoder passwordEncoder, StringRedisTemplate redis,
                       SecurityContextRepository securityContextRepository, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.authTokenMapper = authTokenMapper;
        this.emailOutboxMapper = emailOutboxMapper;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.securityContextRepository = securityContextRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RegisterResult register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        Long existing = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        Role customer = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "CUSTOMER"));
        if (customer == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "角色数据缺失：CUSTOMER");
        }
        UserRole link = new UserRole();
        link.setUserId(user.getId());
        link.setRoleId(customer.getId());
        userRoleMapper.insert(link);

        String rawToken = randomToken();
        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        token.setType("VERIFY");
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(LocalDateTime.now().plus(VERIFY_TOKEN_TTL));
        authTokenMapper.insert(token);

        EmailOutbox outbox = new EmailOutbox();
        outbox.setToEmail(email);
        outbox.setTemplate("EMAIL_VERIFY");
        outbox.setBizKey("verify:" + user.getId() + ":" + token.getId());
        outbox.setStatus("PENDING");
        outbox.setPayloadJson(writePayload(Map.of(
                "nickname", user.getNickname(),
                "verifyUrl", "/verify-email?token=" + rawToken,
                "expireMinutes", VERIFY_TOKEN_TTL.toMinutes())));
        emailOutboxMapper.insert(outbox);

        return new RegisterResult(user.getId(), email, true);
    }

    public UserView login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = request.email().trim().toLowerCase();
        String lockKey = LOGIN_KEY_PREFIX + email;

        String failures = redis.opsForValue().get(lockKey);
        if (failures != null && Integer.parseInt(failures) >= MAX_LOGIN_FAILURES) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "尝试次数过多，请 15 分钟后再试");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailure(lockKey);
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        redis.delete(lockKey);

        User touched = new User();
        touched.setId(user.getId());
        touched.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(touched);

        List<String> roles = rolesOf(user.getId());
        var authorities = roles.stream().map(code -> new SimpleGrantedAuthority("ROLE_" + code)).toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user.getId(), null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return toView(user, roles);
    }

    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public UserView currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return toView(user, rolesOf(userId));
    }

    private void registerFailure(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, LOCK_DURATION);
        }
        if (count != null && count > MAX_LOGIN_FAILURES) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "尝试次数过多，请 15 分钟后再试");
        }
    }

    private List<String> rolesOf(Long userId) {
        List<UserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = links.stream().map(UserRole::getRoleId).toList();
        return roleMapper.selectBatchIds(roleIds).stream().map(Role::getCode).toList();
    }

    private UserView toView(User user, List<String> roles) {
        return new UserView(user.getId(), user.getNickname(), user.getEmail(), user.getPhone(),
                user.getEmailVerifiedAt() != null, roles);
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邮件内容序列化失败");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }
}
