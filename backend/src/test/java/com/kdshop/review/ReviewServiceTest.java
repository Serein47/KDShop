package com.kdshop.review;

import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.review.dto.CreateReviewRequest;
import com.kdshop.review.mapper.ReviewMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewMapper reviewMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private UserMapper userMapper;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewMapper, orderItemMapper, merchantOrderMapper, orderMapper, userMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reviewingTwiceIsRejected() {
        when(orderItemMapper.selectById(9L)).thenReturn(item());
        when(orderMapper.selectById(5L)).thenReturn(order());
        when(merchantOrderMapper.selectById(3L)).thenReturn(sub("COMPLETED"));
        when(reviewMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(9L, new CreateReviewRequest(5, "很好")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.REVIEW_EXISTS);

        verify(reviewMapper, never()).insert(any(com.kdshop.review.entity.Review.class));
    }

    @Test
    void cannotReviewBeforeCompletion() {
        when(orderItemMapper.selectById(9L)).thenReturn(item());
        when(orderMapper.selectById(5L)).thenReturn(order());
        when(merchantOrderMapper.selectById(3L)).thenReturn(sub("SHIPPED"));

        assertThatThrownBy(() -> service.create(9L, new CreateReviewRequest(5, "很好")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SUB_ORDER_STATE_INVALID);
    }

    @Test
    void creatingReviewMasksNothingAndReturnsNickname() {
        when(orderItemMapper.selectById(9L)).thenReturn(item());
        when(orderMapper.selectById(5L)).thenReturn(order());
        when(merchantOrderMapper.selectById(3L)).thenReturn(sub("COMPLETED"));
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        User user = new User();
        user.setId(1L);
        user.setNickname("张三");
        when(userMapper.selectById(1L)).thenReturn(user);

        var view = service.create(9L, new CreateReviewRequest(4, "还不错"));

        assertThat(view.rating()).isEqualTo(4);
        assertThat(view.nickname()).isEqualTo("张三");
        verify(reviewMapper).insert(any(com.kdshop.review.entity.Review.class));
    }

    private OrderItem item() {
        OrderItem item = new OrderItem();
        item.setId(9L);
        item.setOrderId(5L);
        item.setMerchantOrderId(3L);
        item.setProductId(1L);
        item.setMerchantId(1L);
        return item;
    }

    private Order order() {
        Order order = new Order();
        order.setId(5L);
        order.setUserId(1L);
        return order;
    }

    private MerchantOrder sub(String status) {
        MerchantOrder sub = new MerchantOrder();
        sub.setId(3L);
        sub.setStatus(status);
        return sub;
    }
}
