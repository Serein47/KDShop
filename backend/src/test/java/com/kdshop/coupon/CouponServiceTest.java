package com.kdshop.coupon;

import com.kdshop.auth.entity.Role;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.checkout.entity.Coupon;
import com.kdshop.checkout.mapper.CouponMapper;
import com.kdshop.checkout.mapper.UserCouponMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.coupon.dto.CreateCouponRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock private CouponMapper couponMapper;
    @Mock private UserCouponMapper userCouponMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private com.kdshop.auth.mapper.UserMapper userMapper;

    private CouponService service;

    @BeforeEach
    void setUp() {
        service = new CouponService(couponMapper, userCouponMapper, roleMapper, userRoleMapper, userMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void claimingTwiceIsRejected() {
        Coupon coupon = activeCoupon();
        when(couponMapper.selectById(1L)).thenReturn(coupon);
        when(userCouponMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.claim(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_CLAIMED);

        verify(userCouponMapper, never()).insert(any(com.kdshop.checkout.entity.UserCoupon.class));
    }

    @Test
    void expiredCouponCannotBeClaimed() {
        Coupon coupon = activeCoupon();
        coupon.setEndsAt(LocalDateTime.now().minusDays(1));
        when(couponMapper.selectById(1L)).thenReturn(coupon);

        assertThatThrownBy(() -> service.claim(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.COUPON_UNAVAILABLE);
    }

    @Test
    void creatingCouponRequiresAdmin() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.create(new CreateCouponRequest(null, "测试券", "THRESHOLD",
                1000, 5000, 10, LocalDateTime.now(), LocalDateTime.now().plusDays(7))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);

        verify(couponMapper, never()).insert(any(Coupon.class));
    }

    private Coupon activeCoupon() {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setName("新人立减 10 元");
        coupon.setType("FIXED");
        coupon.setValueCent(1000);
        coupon.setMinAmountCent(0);
        coupon.setTotal(100);
        coupon.setUsedCount(0);
        coupon.setStatus("ON");
        coupon.setStartsAt(LocalDateTime.now().minusDays(1));
        coupon.setEndsAt(LocalDateTime.now().plusDays(30));
        return coupon;
    }
}
