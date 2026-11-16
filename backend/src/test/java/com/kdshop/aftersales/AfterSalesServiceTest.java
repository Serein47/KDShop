package com.kdshop.aftersales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.aftersales.dto.ApplyAfterSaleRequest;
import com.kdshop.aftersales.dto.InspectRequest;
import com.kdshop.aftersales.dto.ReturnShipmentRequest;
import com.kdshop.aftersales.dto.ReviewAfterSaleRequest;
import com.kdshop.aftersales.dto.VerdictRequest;
import com.kdshop.aftersales.entity.AfterSale;
import com.kdshop.aftersales.entity.AfterSaleLog;
import com.kdshop.aftersales.mapper.AfterSaleLogMapper;
import com.kdshop.aftersales.mapper.AfterSaleMapper;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.catalog.mapper.SkuMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.MetaProperties;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.entity.OrderItem;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderItemMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.refund.RefundService;
import com.kdshop.refund.entity.Refund;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AfterSalesServiceTest {

    @Mock private AfterSaleMapper afterSaleMapper;
    @Mock private AfterSaleLogMapper logMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private ShopMapper shopMapper;
    @Mock private UserMapper userMapper;
    @Mock private RefundService refundService;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;

    private AfterSalesService service;
    private final MetaProperties meta = new MetaProperties(
            "CNY", "Asia/Shanghai", 9900, 1000, 15, 15, 7, 24, 3, 7, 15, 48, 7, 48);

    @BeforeEach
    void setUp() {
        service = new AfterSalesService(afterSaleMapper, logMapper, merchantOrderMapper, orderMapper,
                orderItemMapper, skuMapper, shopMapper, userMapper, refundService, new ObjectMapper(), meta);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "userRoleMapper", userRoleMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void applyRejectsSubOrderNotCompleted() {
        stubOrderAndSub("SHIPPED", LocalDateTime.now().minusDays(1), "NORMAL");

        assertThatThrownBy(() -> service.apply("KD202611160001", "KD202611160001-1", request("REFUND_ONLY", "QUALITY")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AFTER_SALES_NOT_ELIGIBLE);
        verify(afterSaleMapper, never()).insert(any(AfterSale.class));
    }

    @Test
    void applyRejectsOutsideFifteenDayWindow() {
        stubOrderAndSub("COMPLETED", LocalDateTime.now().minusDays(20), "NORMAL");

        assertThatThrownBy(() -> service.apply("KD202611160001", "KD202611160001-1", request("REFUND_ONLY", "QUALITY")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AFTER_SALES_NOT_ELIGIBLE);
    }

    @Test
    void customOrderOnlyAcceptsQualityOrDescriptionReason() {
        stubOrderAndSub("COMPLETED", LocalDateTime.now().minusDays(1), "CUSTOM");

        assertThatThrownBy(() -> service.apply("KD202611160001", "KD202611160001-1",
                request("REFUND_ONLY", "NO_LONGER_NEEDED")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AFTER_SALES_NOT_ELIGIBLE);
    }

    @Test
    void applyCreatesAfterSaleForRefundableAmount() {
        stubOrderAndSub("COMPLETED", LocalDateTime.now().minusDays(2), "NORMAL");
        when(afterSaleMapper.selectList(any())).thenReturn(List.of());

        var view = service.apply("KD202611160001", "KD202611160001-1",
                new ApplyAfterSaleRequest("REFUND_ONLY", "QUALITY", "杯口有缺口", List.of("a.jpg", "b.jpg")));

        assertThat(view.status()).isEqualTo("APPLIED");
        assertThat(view.refundAmount()).isEqualTo(12800);
        assertThat(view.type()).isEqualTo("REFUND_ONLY");
        assertThat(view.evidence()).containsExactly("a.jpg", "b.jpg");
        verify(afterSaleMapper).insert(any(AfterSale.class));
        verify(logMapper).insert(any(AfterSaleLog.class));
    }

    @Test
    void applyRejectedWhenActiveAfterSaleExists() {
        stubOrderAndSub("COMPLETED", LocalDateTime.now().minusDays(2), "NORMAL");
        AfterSale open = new AfterSale();
        open.setStatus("APPLIED");
        when(afterSaleMapper.selectList(any())).thenReturn(List.of(open));

        assertThatThrownBy(() -> service.apply("KD202611160001", "KD202611160001-1", request("REFUND_ONLY", "QUALITY")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AFTER_SALES_EXISTS);
    }

    @Test
    void approvingRefundOnlyExecutesRefundAndCompletes() {
        loginAsMerchant(shop(1L));
        AfterSale afterSale = existing("APPLIED", "REFUND_ONLY");
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);
        MerchantOrder sub = sub("COMPLETED", LocalDateTime.now().minusDays(2), 12800);
        when(merchantOrderMapper.selectById(9L)).thenReturn(sub);
        Refund refund = new Refund();
        refund.setRefundNo("RF20261116001");
        when(refundService.executeForAfterSales(eq(sub), eq(12800), anyString(), eq("MERCHANT"), eq(1L)))
                .thenReturn(refund);

        var view = service.review("AS20261116001", new ReviewAfterSaleRequest(true, null));

        assertThat(view.status()).isEqualTo("REFUNDED");
        assertThat(view.refundNo()).isEqualTo("RF20261116001");
        verify(refundService).executeForAfterSales(eq(sub), eq(12800), anyString(), eq("MERCHANT"), eq(1L));
    }

    @Test
    void approvingReturnRefundMovesToReturning() {
        loginAsMerchant(shop(1L));
        AfterSale afterSale = existing("APPLIED", "RETURN_REFUND");
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);

        var view = service.review("AS20261116001", new ReviewAfterSaleRequest(true, null));

        assertThat(view.status()).isEqualTo("RETURNING");
        verify(refundService, never()).executeForAfterSales(any(), anyInt(), anyString(), anyString(), any());
    }

    @Test
    void rejectingRequiresReason() {
        loginAsMerchant(shop(1L));
        when(afterSaleMapper.selectOne(any())).thenReturn(existing("APPLIED", "REFUND_ONLY"));

        assertThatThrownBy(() -> service.review("AS20261116001", new ReviewAfterSaleRequest(false, " ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void rejectingStoresReasonAndAllowsOneReapply() {
        loginAsMerchant(shop(1L));
        AfterSale afterSale = existing("APPLIED", "REFUND_ONLY");
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);

        var rejected = service.review("AS20261116001", new ReviewAfterSaleRequest(false, "凭证不足"));
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.rejectReason()).isEqualTo("凭证不足");

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);
        var reapplied = service.reapply("AS20261116001", request("RETURN_REFUND", "QUALITY"));

        assertThat(reapplied.status()).isEqualTo("APPLIED");
        assertThat(reapplied.reapplyCount()).isEqualTo(1);
        assertThat(reapplied.type()).isEqualTo("RETURN_REFUND");
    }

    @Test
    void reapplyIsRefusedAfterTheSingleChanceIsUsed() {
        AfterSale afterSale = existing("REJECTED", "REFUND_ONLY");
        afterSale.setReapplyCount(1);
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);

        assertThatThrownBy(() -> service.reapply("AS20261116001", request("REFUND_ONLY", "QUALITY")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AFTER_SALES_STATE_INVALID);
    }

    @Test
    void submitReturnStoresTracking() {
        AfterSale afterSale = existing("RETURNING", "RETURN_REFUND");
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);

        var view = service.submitReturn("AS20261116001", new ReturnShipmentRequest("顺丰", "SF1001"));

        assertThat(view.returnCarrier()).isEqualTo("顺丰");
        assertThat(view.returnTrackingNo()).isEqualTo("SF1001");
        assertThat(view.returnShippedAt()).isNotNull();
        verify(afterSaleMapper).updateById(afterSale);
    }

    @Test
    void markReceivedStartsInspectCountdown() {
        loginAsMerchant(shop(1L));
        AfterSale afterSale = existing("RETURNING", "RETURN_REFUND");
        afterSale.setReturnTrackingNo("SF1001");
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);

        var view = service.markReceived("AS20261116001");

        assertThat(view.status()).isEqualTo("RECEIVED");
        assertThat(view.receivedAt()).isNotNull();
        assertThat(view.inspectDueAt()).isNotNull();
    }

    @Test
    void inspectGoodRestoresStockAndRefunds() {
        loginAsMerchant(shop(1L));
        AfterSale afterSale = existing("RECEIVED", "RETURN_REFUND");
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);
        when(afterSaleMapper.update(any(), any())).thenReturn(1);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(3L, 2)));
        when(merchantOrderMapper.selectById(9L)).thenReturn(sub("COMPLETED", LocalDateTime.now(), 12800));
        Refund refund = new Refund();
        refund.setRefundNo("RF20261116002");
        when(refundService.executeForAfterSales(any(), eq(12800), anyString(), eq("MERCHANT"), eq(1L)))
                .thenReturn(refund);

        var view = service.inspect("AS20261116001", new InspectRequest("GOOD", "包装完好", List.of()));

        assertThat(view.status()).isEqualTo("REFUNDED");
        assertThat(view.refundNo()).isEqualTo("RF20261116002");
        verify(skuMapper).restoreStock(3L, 2);
    }

    @Test
    void inspectDefectiveRefundsWithoutRestoringStock() {
        loginAsMerchant(shop(1L));
        AfterSale afterSale = existing("RECEIVED", "RETURN_REFUND");
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);
        when(afterSaleMapper.update(any(), any())).thenReturn(1);
        when(merchantOrderMapper.selectById(9L)).thenReturn(sub("COMPLETED", LocalDateTime.now(), 12800));
        Refund refund = new Refund();
        refund.setRefundNo("RF20261116003");
        when(refundService.executeForAfterSales(any(), eq(12800), anyString(), eq("MERCHANT"), eq(1L)))
                .thenReturn(refund);

        var view = service.inspect("AS20261116001", new InspectRequest("DEFECTIVE", "杯口磕碰", List.of()));

        assertThat(view.status()).isEqualTo("REFUNDED");
        assertThat(view.inspectResult()).isEqualTo("DEFECTIVE");
        assertThat(view.responsibility()).isEqualTo("MERCHANT");
        verify(skuMapper, never()).restoreStock(any(), anyInt());
    }

    @Test
    void autoInspectAfterTimeoutTreatsAsGood() {
        AfterSale afterSale = existing("RECEIVED", "RETURN_REFUND");
        when(afterSaleMapper.selectById(1L)).thenReturn(afterSale);
        when(afterSaleMapper.update(any(), any())).thenReturn(1);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item(3L, 1)));
        when(merchantOrderMapper.selectById(9L)).thenReturn(sub("COMPLETED", LocalDateTime.now(), 12800));
        Refund refund = new Refund();
        refund.setRefundNo("RF20261116004");
        when(refundService.executeForAfterSales(any(), eq(12800), anyString(), eq("SYSTEM"), any()))
                .thenReturn(refund);

        assertThat(service.autoInspectGood(1L)).isTrue();
        verify(skuMapper).restoreStock(3L, 1);
        verify(refundService).executeForAfterSales(any(), eq(12800), anyString(), eq("SYSTEM"), any());
    }

    @Test
    void overdueReturnIsClosedOnlyWhenTrackingMissing() {
        AfterSale afterSale = existing("RETURNING", "RETURN_REFUND");
        when(afterSaleMapper.selectById(1L)).thenReturn(afterSale);
        when(afterSaleMapper.update(any(), any())).thenReturn(1);

        assertThat(service.closeOverdueReturn(1L)).isTrue();

        AfterSale shipped = existing("RETURNING", "RETURN_REFUND");
        shipped.setReturnTrackingNo("SF1001");
        when(afterSaleMapper.selectById(1L)).thenReturn(shipped);
        assertThat(service.closeOverdueReturn(1L)).isFalse();
    }

    private OrderItem item(Long skuId, int quantity) {
        OrderItem item = new OrderItem();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void autoReviewRefundsRefundOnlyAfterMerchantTimeout() {
        AfterSale afterSale = existing("APPLIED", "REFUND_ONLY");
        afterSale.setReviewDueAt(LocalDateTime.now().minusHours(1));
        when(afterSaleMapper.selectById(1L)).thenReturn(afterSale);
        when(afterSaleMapper.update(any(), any())).thenReturn(1);
        when(merchantOrderMapper.selectById(9L)).thenReturn(sub("COMPLETED", LocalDateTime.now(), 12800));
        Refund refund = new Refund();
        refund.setRefundNo("RF20261116005");
        when(refundService.executeForAfterSales(any(), eq(12800), anyString(), eq("SYSTEM"), any()))
                .thenReturn(refund);

        assertThat(service.autoReview(1L)).isTrue();
        verify(refundService).executeForAfterSales(any(), eq(12800), anyString(), eq("SYSTEM"), any());
    }

    @Test
    void autoReviewEscalatesReturnRefundInsteadOfRefunding() {
        AfterSale afterSale = existing("APPLIED", "RETURN_REFUND");
        afterSale.setReviewDueAt(LocalDateTime.now().minusHours(1));
        when(afterSaleMapper.selectById(1L)).thenReturn(afterSale);
        when(afterSaleMapper.update(any(), any())).thenReturn(1);

        assertThat(service.autoReview(1L)).isTrue();
        verify(refundService, never()).executeForAfterSales(any(), anyInt(), anyString(), anyString(), any());
    }

    @Test
    void autoReviewDoesNothingBeforeDeadline() {
        AfterSale afterSale = existing("APPLIED", "REFUND_ONLY");
        afterSale.setReviewDueAt(LocalDateTime.now().plusHours(5));
        when(afterSaleMapper.selectById(1L)).thenReturn(afterSale);

        assertThat(service.autoReview(1L)).isFalse();
        verify(afterSaleMapper, never()).update(any(), any());
    }

    @Test
    void verdictApprovesRefundOnlyAndRecordsResponsibility() {
        loginAsAdmin();
        AfterSale afterSale = existing("ESCALATED", "REFUND_ONLY");
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);
        when(afterSaleMapper.update(any(), any())).thenReturn(1);
        when(merchantOrderMapper.selectById(9L)).thenReturn(sub("COMPLETED", LocalDateTime.now(), 12800));
        Refund refund = new Refund();
        refund.setRefundNo("RF20261116006");
        when(refundService.executeForAfterSales(any(), eq(12800), anyString(), eq("ADMIN"), eq(1L)))
                .thenReturn(refund);

        var view = service.verdict("AS20261116001",
                new VerdictRequest("APPROVE", "MERCHANT", "商家责任，平台同意退款"));

        assertThat(view.status()).isEqualTo("REFUNDED");
        assertThat(view.responsibility()).isEqualTo("MERCHANT");
        assertThat(view.verdictNote()).isEqualTo("商家责任，平台同意退款");
    }

    @Test
    void verdictRejectsWithReason() {
        loginAsAdmin();
        AfterSale afterSale = existing("ESCALATED", "RETURN_REFUND");
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);
        when(afterSaleMapper.update(any(), any())).thenReturn(1);

        var view = service.verdict("AS20261116001",
                new VerdictRequest("REJECT", "CUSTOMER", "非质量问题，平台驳回"));

        assertThat(view.status()).isEqualTo("REJECTED");
        assertThat(view.rejectReason()).isEqualTo("非质量问题，平台驳回");
    }

    @Test
    void verdictApprovesReturnAndWaitsForShipment() {
        loginAsAdmin();
        AfterSale afterSale = existing("ESCALATED", "RETURN_REFUND");
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);
        when(afterSaleMapper.update(any(), any())).thenReturn(1);

        var view = service.verdict("AS20261116001",
                new VerdictRequest("APPROVE", "LOGISTICS", "物流破损，平台同意退货"));

        assertThat(view.status()).isEqualTo("RETURNING");
        assertThat(view.returnDueAt()).isNotNull();
    }

    @Test
    void verdictRequiresValidResponsibility() {
        loginAsAdmin();
        when(afterSaleMapper.selectOne(any())).thenReturn(existing("ESCALATED", "REFUND_ONLY"));

        assertThatThrownBy(() -> service.verdict("AS20261116001",
                new VerdictRequest("APPROVE", "SOMEONE", "原因")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void verdictIsForbiddenForNonAdmin() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.verdict("AS20261116001",
                new VerdictRequest("APPROVE", "MERCHANT", "原因")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    private void loginAsAdmin() {
        Role role = new Role();
        role.setId(5L);
        role.setCode("ADMIN");
        when(roleMapper.selectOne(any())).thenReturn(role);
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
    }

    private void stubOrderAndSub(String subStatus, LocalDateTime completedAt, String orderType) {
        Order order = new Order();
        order.setId(10L);
        order.setOrderNo("KD202611160001");
        order.setUserId(1L);
        order.setOrderType(orderType);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(merchantOrderMapper.selectOne(any())).thenReturn(sub(subStatus, completedAt, 12800));
    }

    private MerchantOrder sub(String status, LocalDateTime completedAt, int pay) {
        MerchantOrder sub = new MerchantOrder();
        sub.setId(9L);
        sub.setOrderId(10L);
        sub.setSubOrderNo("KD202611160001-1");
        sub.setMerchantId(1L);
        sub.setStatus(status);
        sub.setCompletedAt(completedAt);
        sub.setPayAmountCent(pay);
        sub.setRefundedAmountCent(0);
        return sub;
    }

    private AfterSale existing(String status, String type) {
        AfterSale afterSale = new AfterSale();
        afterSale.setId(1L);
        afterSale.setAsNo("AS20261116001");
        afterSale.setMerchantOrderId(9L);
        afterSale.setOrderId(10L);
        afterSale.setMerchantId(1L);
        afterSale.setUserId(1L);
        afterSale.setStatus(status);
        afterSale.setType(type);
        afterSale.setRefundAmountCent(12800);
        afterSale.setReapplyCount(0);
        return afterSale;
    }

    private ApplyAfterSaleRequest request(String type, String reason) {
        return new ApplyAfterSaleRequest(type, reason, "说明", List.of());
    }

    private Shop shop(Long id) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setUserId(1L);
        shop.setStatus("ACTIVE");
        return shop;
    }

    private void loginAsMerchant(Shop shop) {
        when(shopMapper.selectOne(any())).thenReturn(shop);
    }
}
