package com.kdshop.coupon;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.checkout.entity.Coupon;
import com.kdshop.checkout.entity.UserCoupon;
import com.kdshop.checkout.mapper.CouponMapper;
import com.kdshop.checkout.mapper.UserCouponMapper;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.coupon.dto.CouponView;
import com.kdshop.coupon.dto.CreateCouponRequest;
import com.kdshop.coupon.dto.MyCouponView;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 领券中心与我的券；结算时由 CheckoutService/OrderService 负责可用性与核销。 */
@Service
public class CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    public CouponService(CouponMapper couponMapper, UserCouponMapper userCouponMapper,
                         RoleMapper roleMapper, UserRoleMapper userRoleMapper) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public List<CouponView> available() {
        Long userId = currentUserIdOrNull();
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, "ON")
                .le(Coupon::getStartsAt, now)
                .gt(Coupon::getEndsAt, now)
                .orderByAsc(Coupon::getEndsAt));
        Map<Long, Long> claimedCounts = coupons.isEmpty() ? Map.of()
                : userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                        .in(UserCoupon::getCouponId, coupons.stream().map(Coupon::getId).toList()))
                .stream().collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        List<Long> mine = userId == null ? List.of()
                : userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)).stream()
                .map(UserCoupon::getCouponId).toList();

        return coupons.stream().map(c -> {
            int claimed = claimedCounts.getOrDefault(c.getId(), 0L).intValue();
            return new CouponView(c.getId(), c.getName(), c.getType(), c.getValueCent(), c.getMinAmountCent(),
                    Math.max(c.getTotal() - claimed, 0), mine.contains(c.getId()), c.getEndsAt());
        }).toList();
    }

    @Transactional
    public MyCouponView claim(Long couponId) {
        Long userId = currentUserId();
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !"ON".equals(coupon.getStatus())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券不存在或已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartsAt()) || now.isAfter(coupon.getEndsAt())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "不在领取时间内");
        }
        Long exists = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_CLAIMED);
        }
        Long claimed = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, couponId));
        if (claimed != null && claimed >= coupon.getTotal()) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券已被领完");
        }
        UserCoupon link = new UserCoupon();
        link.setUserId(userId);
        link.setCouponId(couponId);
        link.setStatus("UNUSED");
        userCouponMapper.insert(link);
        return toMine(link, coupon);
    }

    public List<MyCouponView> mine() {
        Long userId = currentUserId();
        List<UserCoupon> owned = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getId));
        if (owned.isEmpty()) {
            return List.of();
        }
        Map<Long, Coupon> coupons = couponMapper.selectBatchIds(
                        owned.stream().map(UserCoupon::getCouponId).distinct().toList()).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));
        return owned.stream().map(u -> {
            Coupon c = coupons.get(u.getCouponId());
            return c == null ? null : toMine(u, c);
        }).filter(java.util.Objects::nonNull).toList();
    }

    public List<CouponView> adminList() {
        requireAdmin();
        LocalDateTime now = LocalDateTime.now();
        return couponMapper.selectList(new LambdaQueryWrapper<Coupon>().orderByDesc(Coupon::getId)).stream()
                .map(c -> {
                    Long claimed = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                            .eq(UserCoupon::getCouponId, c.getId()));
                    return new CouponView(c.getId(), c.getName(), c.getType(), c.getValueCent(),
                            c.getMinAmountCent(), Math.max(c.getTotal() - (claimed == null ? 0 : claimed.intValue()), 0),
                            !now.isBefore(c.getStartsAt()) && !now.isAfter(c.getEndsAt()), c.getEndsAt());
                }).toList();
    }

    @Transactional
    public CouponView create(CreateCouponRequest request) {
        requireAdmin();
        if (request.endsAt().isBefore(request.startsAt())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束时间不能早于开始时间");
        }
        Coupon coupon = new Coupon();
        coupon.setCode(StringUtils.hasText(request.code()) ? request.code().trim()
                : "CP" + System.currentTimeMillis());
        coupon.setName(request.name().trim());
        coupon.setType(request.type().toUpperCase());
        coupon.setValueCent(request.value());
        coupon.setMinAmountCent(request.minAmount());
        coupon.setTotal(request.total());
        coupon.setUsedCount(0);
        coupon.setScope("PLATFORM");
        coupon.setStartsAt(request.startsAt());
        coupon.setEndsAt(request.endsAt());
        coupon.setStatus("ON");
        couponMapper.insert(coupon);
        return new CouponView(coupon.getId(), coupon.getName(), coupon.getType(), coupon.getValueCent(),
                coupon.getMinAmountCent(), coupon.getTotal(), true, coupon.getEndsAt());
    }

    private MyCouponView toMine(UserCoupon link, Coupon coupon) {
        return new MyCouponView(link.getId(), coupon.getId(), coupon.getName(), coupon.getType(),
                coupon.getValueCent(), coupon.getMinAmountCent(), link.getStatus(), coupon.getEndsAt(),
                link.getUsedAt());
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
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private Long currentUserIdOrNull() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return null;
        }
        return userId;
    }
}
