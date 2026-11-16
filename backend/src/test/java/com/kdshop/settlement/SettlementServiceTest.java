package com.kdshop.settlement;

import com.kdshop.auth.entity.Role;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.penalty.entity.Penalty;
import com.kdshop.penalty.mapper.PenaltyMapper;
import com.kdshop.settlement.entity.Settlement;
import com.kdshop.settlement.entity.SettlementItem;
import com.kdshop.settlement.mapper.SettlementItemMapper;
import com.kdshop.settlement.mapper.SettlementMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock private SettlementMapper settlementMapper;
    @Mock private SettlementItemMapper itemMapper;
    @Mock private MerchantOrderMapper merchantOrderMapper;
    @Mock private ShopMapper shopMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private PenaltyMapper penaltyMapper;

    private SettlementService service;

    @BeforeEach
    void setUp() {
        service = new SettlementService(settlementMapper, itemMapper, merchantOrderMapper, shopMapper,
                userRoleMapper, roleMapper, penaltyMapper);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(8L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generatingDeductsCommissionAndMarksOrdersSettled() {
        when(roleMapper.selectOne(any())).thenReturn(role("ADMIN"));
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        Shop shop = new Shop();
        shop.setId(1L);
        shop.setShopName("山野杂货");
        shop.setStatus("ACTIVE");
        shop.setCommissionRate(500);
        when(shopMapper.selectList(any())).thenReturn(List.of(shop));
        when(settlementMapper.selectCount(any())).thenReturn(0L);

        MerchantOrder sub = new MerchantOrder();
        sub.setId(9L);
        sub.setPayAmountCent(25600);
        sub.setRefundedAmountCent(0);
        sub.setStatus("COMPLETED");
        sub.setCompletedAt(LocalDateTime.now().minusDays(1));
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(sub));

        var views = service.generate(LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(views).hasSize(1);
        assertThat(views.get(0).grossAmount()).isEqualTo(25600);
        assertThat(views.get(0).commission()).isEqualTo(1280);
        assertThat(views.get(0).netAmount()).isEqualTo(24320);
        verify(settlementMapper).insert(any(Settlement.class));
        verify(itemMapper).insert(any(SettlementItem.class));
        verify(merchantOrderMapper).updateById(any(MerchantOrder.class));
    }

    @Test
    void generatingDeductsPendingPenaltiesIntoNetAmount() {
        when(roleMapper.selectOne(any())).thenReturn(role("ADMIN"));
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
        when(shopMapper.selectList(any())).thenReturn(List.of(shop(1L, 500)));
        when(settlementMapper.selectCount(any())).thenReturn(0L);
        when(merchantOrderMapper.selectList(any())).thenReturn(List.of(completedSub(9L, 25600)));
        when(penaltyMapper.selectList(any())).thenReturn(List.of(penalty(9L, 1, 250), penalty(9L, 2, 250)));

        var views = service.generate(LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(views).hasSize(1);
        assertThat(views.get(0).penalty()).isEqualTo(500);
        assertThat(views.get(0).netAmount()).isEqualTo(23820);

        ArgumentCaptor<SettlementItem> captor = ArgumentCaptor.forClass(SettlementItem.class);
        verify(itemMapper).insert(captor.capture());
        assertThat(captor.getValue().getPenaltyCent()).isEqualTo(500);
        assertThat(captor.getValue().getNetAmountCent()).isEqualTo(23820);
        verify(penaltyMapper).update(any(), any());
    }

    @Test
    void detailReturnsItemBreakdownForOwningMerchant() {
        Settlement settlement = settlement(1L, 500);
        when(settlementMapper.selectOne(any())).thenReturn(settlement);
        when(roleMapper.selectOne(any())).thenReturn(null);
        when(shopMapper.selectOne(any())).thenReturn(shop(1L, 500));
        when(shopMapper.selectById(1L)).thenReturn(shop(1L, 500));

        SettlementItem item = new SettlementItem();
        item.setSettlementId(settlement.getId());
        item.setMerchantOrderId(9L);
        item.setGrossAmountCent(25600);
        item.setCommissionCent(1280);
        item.setRefundCent(0);
        item.setPenaltyCent(500);
        item.setNetAmountCent(23820);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        MerchantOrder sub = completedSub(9L, 25600);
        sub.setSubOrderNo("KD202611160001-1");
        when(merchantOrderMapper.selectBatchIds(any())).thenReturn(List.of(sub));

        var detail = service.detail("ST20261116001");

        assertThat(detail.settlement().penalty()).isEqualTo(500);
        assertThat(detail.items()).hasSize(1);
        assertThat(detail.items().get(0).subOrderNo()).isEqualTo("KD202611160001-1");
        assertThat(detail.items().get(0).penalty()).isEqualTo(500);
        assertThat(detail.items().get(0).netAmount()).isEqualTo(23820);
    }

    @Test
    void detailOfAnotherMerchantsSettlementIsForbidden() {
        when(settlementMapper.selectOne(any())).thenReturn(settlement(2L, 0));
        when(roleMapper.selectOne(any())).thenReturn(null);
        when(shopMapper.selectOne(any())).thenReturn(shop(1L, 500));

        assertThatThrownBy(() -> service.detail("ST20261116002"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    @Test
    void settlementRequiresAdmin() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.listAll())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    private Shop shop(Long id, int rate) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setShopName("山野杂货");
        shop.setStatus("ACTIVE");
        shop.setCommissionRate(rate);
        return shop;
    }

    private MerchantOrder completedSub(Long id, int payAmountCent) {
        MerchantOrder sub = new MerchantOrder();
        sub.setId(id);
        sub.setPayAmountCent(payAmountCent);
        sub.setRefundedAmountCent(0);
        sub.setStatus("COMPLETED");
        sub.setCompletedAt(LocalDateTime.now().minusDays(1));
        return sub;
    }

    private Penalty penalty(Long subOrderId, int day, int amountCent) {
        Penalty penalty = new Penalty();
        penalty.setId((long) day);
        penalty.setMerchantOrderId(subOrderId);
        penalty.setDaysOverdue(day);
        penalty.setAmountCent(amountCent);
        penalty.setStatus("PENDING");
        return penalty;
    }

    private Settlement settlement(Long merchantId, int penaltyCent) {
        Settlement settlement = new Settlement();
        settlement.setId(1L);
        settlement.setSettlementNo(merchantId == 1L ? "ST20261116001" : "ST20261116002");
        settlement.setMerchantId(merchantId);
        settlement.setPeriodStart(LocalDate.now().minusDays(7));
        settlement.setPeriodEnd(LocalDate.now());
        settlement.setGrossAmountCent(25600);
        settlement.setCommissionCent(1280);
        settlement.setRefundCent(0);
        settlement.setPenaltyCent(penaltyCent);
        settlement.setNetAmountCent(25600 - 1280 - penaltyCent);
        settlement.setStatus("PENDING");
        return settlement;
    }

    private Role role(String code) {
        Role role = new Role();
        role.setId(5L);
        role.setCode(code);
        return role;
    }
}
