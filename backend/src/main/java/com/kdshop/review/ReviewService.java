package com.kdshop.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.review.dto.CreateReviewRequest;
import com.kdshop.review.dto.MyReviewView;
import com.kdshop.review.dto.ProductReviewView;
import com.kdshop.review.dto.ReviewView;
import com.kdshop.review.entity.Review;
import com.kdshop.review.mapper.ReviewMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderItemMapper orderItemMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;

    public ReviewService(ReviewMapper reviewMapper, OrderItemMapper orderItemMapper,
                         MerchantOrderMapper merchantOrderMapper, OrderMapper orderMapper, UserMapper userMapper) {
        this.reviewMapper = reviewMapper;
        this.orderItemMapper = orderItemMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public ReviewView create(Long orderItemId, CreateReviewRequest request) {
        Long userId = currentUserId();
        OrderItem item = orderItemMapper.selectById(orderItemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单商品不存在");
        }
        Order order = orderMapper.selectById(item.getOrderId());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        MerchantOrder sub = merchantOrderMapper.selectById(item.getMerchantOrderId());
        if (sub == null || !"COMPLETED".equals(sub.getStatus())) {
            throw new BusinessException(ErrorCode.SUB_ORDER_STATE_INVALID, "确认收货后才能评价");
        }
        Long exists = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderItemId, orderItemId));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.REVIEW_EXISTS);
        }
        Review review = new Review();
        review.setOrderItemId(orderItemId);
        review.setUserId(userId);
        review.setProductId(item.getProductId());
        review.setMerchantId(item.getMerchantId());
        review.setRating(request.rating());
        review.setContent(request.content().trim());
        review.setStatus("VISIBLE");
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);
        User user = userMapper.selectById(userId);
        return new ReviewView(review.getId(), user == null ? "顾客" : user.getNickname(),
                review.getRating(), review.getContent(), review.getCreatedAt());
    }

    public ProductReviewView byProduct(Long productId) {
        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId)
                .eq(Review::getStatus, "VISIBLE")
                .orderByDesc(Review::getId)
                .last("LIMIT 50"));
        if (reviews.isEmpty()) {
            return new ProductReviewView(0, 0, List.of());
        }
        Map<Long, User> users = userMapper.selectBatchIds(
                        reviews.stream().map(Review::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<ReviewView> items = reviews.stream().map(r -> {
            User user = users.get(r.getUserId());
            return new ReviewView(r.getId(), user == null ? "顾客" : mask(user.getNickname()),
                    r.getRating(), r.getContent(), r.getCreatedAt());
        }).toList();
        double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0);
        return new ProductReviewView(Math.round(avg * 10) / 10.0, reviews.size(), items);
    }

    public List<MyReviewView> mine() {
        Long userId = currentUserId();
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getId)).stream()
                .map(r -> new MyReviewView(r.getOrderItemId(), r.getProductId(), r.getRating(),
                        r.getContent(), r.getCreatedAt()))
                .filter(Objects::nonNull).toList();
    }

    private String mask(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "顾客";
        }
        return nickname.length() <= 1 ? nickname : nickname.charAt(0) + "**";
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
