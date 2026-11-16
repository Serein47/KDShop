package com.kdshop.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.admin.dto.AdminUserView;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 平台账号与角色管理：禁用/启用、角色分配；列表与详情脱敏。 */
@Service
public class AdminUserService {

    private static final String SESSION_KEY = "user:sessions:";
    private static final String SPRING_SESSION_KEY = "spring:session:sessions:";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final StringRedisTemplate redis;

    @Autowired(required = false)
    private AuditService auditService;

    public AdminUserService(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                            StringRedisTemplate redis) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.redis = redis;
    }

    public List<AdminUserView> list(String keyword, String status) {
        requireAdmin();
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>().orderByDesc(User::getId).last("LIMIT 100");
        if (StringUtils.hasText(keyword)) {
            query.and(w -> w.like(User::getEmail, keyword).or().like(User::getNickname, keyword));
        }
        if (StringUtils.hasText(status)) {
            query.eq(User::getStatus, status.toUpperCase());
        }
        List<User> users = userMapper.selectList(query);
        if (users.isEmpty()) {
            return List.of();
        }
        List<UserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .in(UserRole::getUserId, users.stream().map(User::getId).toList()));
        Map<Long, String> roleCodes = roleMapper.selectList(new LambdaQueryWrapper<Role>()).stream()
                .collect(Collectors.toMap(Role::getId, Role::getCode));
        Map<Long, List<String>> byUser = links.stream().collect(Collectors.groupingBy(UserRole::getUserId,
                Collectors.mapping(l -> roleCodes.getOrDefault(l.getRoleId(), "?"), Collectors.toList())));
        return users.stream().map(u -> viewOf(u, byUser.getOrDefault(u.getId(), List.of()))).toList();
    }

    public AdminUserView detail(Long userId) {
        requireAdmin();
        return toView(requireUser(userId));
    }

    @Transactional
    public AdminUserView setStatus(Long userId, boolean active, String reason) {
        requireAdmin();
        Long adminId = currentUserId();
        if (userId.equals(adminId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不能禁用自己的账号");
        }
        User user = requireUser(userId);
        String from = user.getStatus();
        user.setStatus(active ? "ACTIVE" : "DISABLED");
        userMapper.updateById(user);
        if (!active) {
            revokeSessions(userId);
        }
        if (auditService != null) {
            auditService.record("ADMIN", adminId, active ? "USER_ENABLE" : "USER_DISABLE", "USER",
                    String.valueOf(userId), Map.of("status", from),
                    Map.of("status", user.getStatus(), "reason", String.valueOf(reason)));
        }
        return toView(user);
    }

    @Transactional
    public AdminUserView assignRoles(Long userId, List<String> roles) {
        requireAdmin();
        Long adminId = currentUserId();
        if (userId.equals(adminId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不能修改自己的角色");
        }
        User user = requireUser(userId);
        List<String> wanted = roles == null ? List.of() : roles.stream().filter(StringUtils::hasText)
                .map(String::toUpperCase).distinct().toList();
        List<Role> all = roleMapper.selectList(new LambdaQueryWrapper<Role>());
        Map<String, Long> byCode = all.stream().collect(Collectors.toMap(Role::getCode, Role::getId));
        List<String> unknown = wanted.stream().filter(c -> !byCode.containsKey(c)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "未知角色：" + String.join(",", unknown));
        }
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (String code : wanted) {
            UserRole link = new UserRole();
            link.setUserId(userId);
            link.setRoleId(byCode.get(code));
            userRoleMapper.insert(link);
        }
        if (auditService != null) {
            auditService.record("ADMIN", adminId, "USER_ROLES_UPDATE", "USER", String.valueOf(userId),
                    Map.of("roles", "before"), Map.of("roles", String.join(",", wanted)));
        }
        return toView(user);
    }

    private AdminUserView toView(User user) {
        List<UserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, user.getId()));
        Map<Long, String> codes = roleMapper.selectList(new LambdaQueryWrapper<Role>()).stream()
                .collect(Collectors.toMap(Role::getId, Role::getCode));
        List<String> roles = links.stream().map(l -> codes.getOrDefault(l.getRoleId(), "?")).toList();
        return viewOf(user, roles);
    }

    /** 列表与详情统一脱敏：邮箱保留首字母与域名，手机保留前 3 后 4。 */
    private AdminUserView viewOf(User user, List<String> roles) {
        return new AdminUserView(user.getId(), maskEmail(user.getEmail()), maskPhone(user.getPhone()),
                user.getNickname(), user.getStatus(), roles, user.getLastLoginAt(), user.getCreatedAt());
    }

    static String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() == 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }

    static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return StringUtils.hasText(phone) ? "***" : "";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 禁用后立即踢下线：清掉该用户在 Redis 里登记的所有 Spring Session。 */
    private void revokeSessions(Long userId) {
        if (redis == null || redis.opsForSet() == null) {
            return;
        }
        String userSessions = SESSION_KEY + userId;
        Set<String> sessionIds = redis.opsForSet().members(userSessions);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (String sessionId : sessionIds) {
                keys.add(SPRING_SESSION_KEY + sessionId);
                keys.add(SPRING_SESSION_KEY + "expires:" + sessionId);
            }
            redis.delete(keys);
        }
        redis.delete(userSessions);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户不存在");
        }
        return user;
    }

    private void requireAdmin() {
        Long userId = currentUserId();
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "ADMIN"));
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId()));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
