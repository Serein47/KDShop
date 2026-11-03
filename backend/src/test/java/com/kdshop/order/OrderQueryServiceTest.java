package com.kdshop.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.catalog.entity.Product;
import com.kdshop.catalog.mapper.ProductMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.MetaProperties;
import com.kdshop.order.dto.OrderDetailView;
import com.kdshop.order.dto.OrderPage;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.entity.OrderStatusLog;
import com.kdshop.order.entity.Shipment;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.order.mapper.OrderStatusLogMapper;
import com.kdshop.order.mapper.ShipmentMapper;
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
class OrderQueryServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderStatusLogMapper statusLogMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ShipmentMapper shipmentMapper;

    private OrderQueryService service;
    private final MetaProperties meta = new MetaProperties("CNY", "Asia/Shanghai", 9900, 1000, 15, 15, 7);

    @BeforeEach
    void setUp() {
        service = new OrderQueryService(orderMapper, merchantOrderMapper, orderItemMapper,
                statusLogMapper, productMapper, shipmentMapper, new ObjectMapper(), meta);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listReturnsSubOrderSummaryAndCover() {
        Order order = order(10L, "KD202610010003", 1L, "PARTIALLY_SHIPPED");
        order.setPayAmountCent(19700);
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(
                sub(3L, "KD202610010003-1", "SHIPPED"),
                sub(4L, "KD202610010003-2", "PENDING_SHIPMENT")));

        OrderItem first = item(3L, 7L, "手作陶瓷杯");
        OrderItem second = item(4L, 11L, "65W 氮化镓充电器");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(first, second));

        Product product = new Product();
        product.setId(7L);
        product.setMainImage("products/7/1.jpg");
        when(productMapper.selectById(7L)).thenReturn(product);

        OrderPage page = service.list(null, 1, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).subOrderCount()).isEqualTo(2);
        assertThat(page.items().get(0).shippedCount()).isEqualTo(1);
        assertThat(page.items().get(0).summary()).isEqualTo("手作陶瓷杯 等 2 件商品");
        assertThat(page.items().get(0).coverImage()).isEqualTo("products/7/1.jpg");
        assertThat(page.items().get(0).expireAt()).isNull();
    }

    @Test
    void invalidStatusIsRejected() {
        assertThatThrownBy(() -> service.list("NOT_A_STATUS", 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void detailOfOthersOrderIsForbidden() {
        Order order = order(10L, "KD202610010003", 99L, "PENDING_PAYMENT");
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertThatThrownBy(() -> service.detail("KD202610010003"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);

        verify(merchantOrderMapper, never()).selectList(any());
    }

    @Test
    void detailReturnsAddressTimelineShipmentAndActions() {
        Order order = order(10L, "KD202610010003", 1L, "SHIPPED");
        order.setAddressJson("""
                {"receiver":"张三","phone":"13800008000","province":"浙江省",
                 "city":"杭州市","district":"西湖区","detail":"文三路 1 号"}
                """);
        order.setPaidAt(LocalDateTime.of(2026, 10, 30, 10, 0));
        when(orderMapper.selectOne(any())).thenReturn(order);

        MerchantOrder sub = sub(3L, "KD202610010003-1", "SHIPPED");
        sub.setShippedAt(LocalDateTime.of(2026, 10, 31, 9, 0));
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));

        OrderItem item = item(3L, 7L, "手作陶瓷杯");
        item.setSkuSpec("{\"颜色\":\"米白\"}");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));

        Shipment shipment = new Shipment();
        shipment.setCarrier("顺丰");
        shipment.setTrackingNo("SF1000000003");
        when(shipmentMapper.selectOne(any())).thenReturn(shipment);

        OrderStatusLog log = new OrderStatusLog();
        log.setToStatus("SHIPPED");
        log.setOperatorType("SYSTEM");
        log.setCreatedAt(LocalDateTime.of(2026, 10, 31, 9, 0));
        when(statusLogMapper.selectList(any())).thenReturn(List.of(log));

        OrderDetailView detail = service.detail("KD202610010003");

        assertThat(detail.address().receiver()).isEqualTo("张三");
        assertThat(detail.timeline()).extracting(OrderDetailView.TimelineEntry::status)
                .containsExactly("PENDING_PAYMENT", "PAID", "SHIPPED");
        assertThat(detail.subOrders()).hasSize(1);
        assertThat(detail.subOrders().get(0).actions()).containsExactly("CONFIRM");
        assertThat(detail.subOrders().get(0).shipment().trackingNo()).isEqualTo("SF1000000003");
        assertThat(detail.subOrders().get(0).items().get(0).spec()).containsEntry("颜色", "米白");
    }

    @Test
    void detailBuildsFallbackTimelineWhenNoLogsExist() {
        Order order = order(11L, "KD202610010002", 1L, "SHIPPED");
        order.setPaidAt(LocalDateTime.of(2026, 10, 30, 10, 0));
        when(orderMapper.selectOne(any())).thenReturn(order);

        MerchantOrder sub = sub(5L, "KD202610010002-2", "SHIPPED");
        sub.setShippedAt(LocalDateTime.of(2026, 10, 31, 9, 0));
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));
        when(orderItemMapper.selectList(any())).thenReturn(List.of());
        when(statusLogMapper.selectList(any())).thenReturn(List.of());

        OrderDetailView detail = service.detail("KD202610010002");

        assertThat(detail.timeline()).extracting(OrderDetailView.TimelineEntry::status)
                .containsExactly("PENDING_PAYMENT", "PAID", "SHIPPED");
        assertThat(detail.subOrders().get(0).shopName()).isEqualTo("店铺 1");
    }

    private Order order(Long id, String no, Long userId, String status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo(no);
        order.setUserId(userId);
        order.setStatus(status);
        order.setMerchantCount(1);
        order.setGoodsAmountCent(12800);
        order.setDiscountCent(0);
        order.setShippingFeeCent(0);
        order.setPayAmountCent(12800);
        order.setCreatedAt(LocalDateTime.of(2026, 10, 30, 9, 0));
        order.setUpdatedAt(LocalDateTime.of(2026, 10, 31, 9, 0));
        return order;
    }

    private MerchantOrder sub(Long id, String no, String status) {
        MerchantOrder sub = new MerchantOrder();
        sub.setId(id);
        sub.setSubOrderNo(no);
        sub.setMerchantId(1L);
        sub.setStatus(status);
        sub.setPayAmountCent(12800);
        return sub;
    }

    private OrderItem item(Long subOrderId, Long productId, String name) {
        OrderItem item = new OrderItem();
        item.setMerchantOrderId(subOrderId);
        item.setProductId(productId);
        item.setProductName(name);
        item.setShopName("山野杂货");
        item.setUnitPriceCent(5800);
        item.setQuantity(1);
        item.setSubtotalCent(5800);
        return item;
    }
}
