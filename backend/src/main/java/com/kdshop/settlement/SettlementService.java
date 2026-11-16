package com.kdshop.settlement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kdshop.admin.AuditService;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.UserRole;
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
import com.kdshop.refund.entity.Refund;
import com.kdshop.refund.mapper.RefundMapper;
import com.kdshop.settlement.dto.PenaltyLineView;
import com.kdshop.settlement.dto.RefundLineView;
import com.kdshop.settlement.dto.SettlementDetailView;
import com.kdshop.settlement.dto.SettlementItemView;
import com.kdshop.settlement.dto.SettlementView;
import com.kdshop.settlement.entity.Settlement;
import com.kdshop.settlement.entity.SettlementItem;
import com.kdshop.settlement.mapper.SettlementItemMapper;
import com.kdshop.settlement.mapper.SettlementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 结算：按商家与周期汇总已完成子单，扣佣金与退款，生成结算单。 */
@Service
public class SettlementService {

    private final SettlementMapper settlementMapper;
    private final SettlementItemMapper itemMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final ShopMapper shopMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PenaltyMapper penaltyMapper;
    private final RefundMapper refundMapper;

    @Autowired(required = false)
    private AuditService auditService;

    public SettlementService(SettlementMapper settlementMapper, SettlementItemMapper itemMapper,
                             MerchantOrderMapper merchantOrderMapper, ShopMapper shopMapper,
                             UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                             PenaltyMapper penaltyMapper, RefundMapper refundMapper) {
        this.settlementMapper = settlementMapper;
        this.itemMapper = itemMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.shopMapper = shopMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.penaltyMapper = penaltyMapper;
        this.refundMapper = refundMapper;
    }

    @Transactional
    public List<SettlementView> generate(LocalDate start, LocalDate end) {
        requireAdmin();
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();

        List<Shop> shops = shopMapper.selectList(new LambdaQueryWrapper<Shop>().eq(Shop::getStatus, "ACTIVE"));
        List<SettlementView> created = new ArrayList<>();

        for (Shop shop : shops) {
            Long exists = settlementMapper.selectCount(new LambdaQueryWrapper<Settlement>()
                    .eq(Settlement::getMerchantId, shop.getId())
                    .eq(Settlement::getPeriodStart, start)
                    .eq(Settlement::getPeriodEnd, end));
            if (exists != null && exists > 0) {
                continue;
            }
            List<MerchantOrder> subs = merchantOrderMapper.selectList(new LambdaQueryWrapper<MerchantOrder>()
                            .eq(MerchantOrder::getMerchantId, shop.getId())
                            .eq(MerchantOrder::getStatus, "COMPLETED")
                            .eq(MerchantOrder::getSettled, 0)
                            // 用 updated_at 纳入周期：售后冻结跨期后解冻的子单能在解冻当期被结算；
                            // 正常完成的子单 updated_at ≈ completed_at，不受影响
                            .ge(MerchantOrder::getUpdatedAt, from)
                            .lt(MerchantOrder::getUpdatedAt, to))
                    // 售后中的子单冻结结算，等关闭（解冻）或退款（直接冲抵）后再进入下一期
                    .stream()
                    .filter(sub -> sub.getAfterSalesFrozen() == null || sub.getAfterSalesFrozen() == 0)
                    .toList();
            if (subs.isEmpty()) {
                continue;
            }

            int rate = shop.getCommissionRate() == null ? 500 : shop.getCommissionRate();
            int gross = 0;
            int commission = 0;
            int refund = 0;
            int penalty = 0;
            Map<Long, List<Penalty>> penaltiesBySub = new java.util.LinkedHashMap<>();
            for (MerchantOrder sub : subs) {
                int pay = sub.getPayAmountCent() == null ? 0 : sub.getPayAmountCent();
                int refunded = sub.getRefundedAmountCent() == null ? 0 : sub.getRefundedAmountCent();
                gross += pay;
                refund += refunded;
                commission += (int) Math.round((pay - refunded) * rate / 10000.0);

                List<Penalty> rows = penaltyMapper.selectList(new LambdaQueryWrapper<Penalty>()
                        .eq(Penalty::getMerchantOrderId, sub.getId())
                        .eq(Penalty::getStatus, "PENDING"));
                penaltiesBySub.put(sub.getId(), rows);
                penalty += rows.stream().mapToInt(p -> p.getAmountCent() == null ? 0 : p.getAmountCent()).sum();
            }
            int net = gross - refund - commission - penalty;

            Settlement settlement = new Settlement();
            settlement.setSettlementNo("ST" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + String.format("%03d", shop.getId()));
            settlement.setMerchantId(shop.getId());
            settlement.setPeriodStart(start);
            settlement.setPeriodEnd(end);
            settlement.setGrossAmountCent(gross);
            settlement.setCommissionCent(commission);
            settlement.setRefundCent(refund);
            settlement.setPenaltyCent(penalty);
            settlement.setNetAmountCent(net);
            settlement.setStatus("PENDING");
            settlementMapper.insert(settlement);

            for (MerchantOrder sub : subs) {
                int pay = sub.getPayAmountCent() == null ? 0 : sub.getPayAmountCent();
                int refunded = sub.getRefundedAmountCent() == null ? 0 : sub.getRefundedAmountCent();
                int subCommission = (int) Math.round((pay - refunded) * rate / 10000.0);
                List<Penalty> rows = penaltiesBySub.getOrDefault(sub.getId(), List.of());
                int subPenalty = rows.stream().mapToInt(p -> p.getAmountCent() == null ? 0 : p.getAmountCent()).sum();

                SettlementItem item = new SettlementItem();
                item.setSettlementId(settlement.getId());
                item.setMerchantOrderId(sub.getId());
                item.setGrossAmountCent(pay);
                item.setCommissionCent(subCommission);
                item.setRefundCent(refunded);
                item.setPenaltyCent(subPenalty);
                item.setNetAmountCent(pay - refunded - subCommission - subPenalty);
                itemMapper.insert(item);

                if (!rows.isEmpty()) {
                    penaltyMapper.update(null, new LambdaUpdateWrapper<Penalty>()
                            .in(Penalty::getId, rows.stream().map(Penalty::getId).toList())
                            .set(Penalty::getStatus, "SETTLED"));
                }

                MerchantOrder patch = new MerchantOrder();
                patch.setId(sub.getId());
                patch.setSettled(1);
                merchantOrderMapper.updateById(patch);
            }
            created.add(toView(settlement, shop, subs.size()));
        }

        if (!created.isEmpty() && auditService != null) {
            auditService.record("ADMIN", currentUserId(), "SETTLEMENT_GENERATE", "SETTLEMENT",
                    start + "~" + end, null, Map.of("count", created.size()));
        }
        return created;
    }

    public List<SettlementView> listAll() {
        requireAdmin();
        return enrich(settlementMapper.selectList(new LambdaQueryWrapper<Settlement>()
                .orderByDesc(Settlement::getId).last("LIMIT 100")));
    }

    public List<SettlementView> listMine() {
        Shop shop = currentShop();
        return enrich(settlementMapper.selectList(new LambdaQueryWrapper<Settlement>()
                .eq(Settlement::getMerchantId, shop.getId())
                .orderByDesc(Settlement::getId).last("LIMIT 100")));
    }

    @Transactional
    public SettlementView confirm(String settlementNo) {
        requireAdmin();
        Settlement settlement = settlementMapper.selectOne(new LambdaQueryWrapper<Settlement>()
                .eq(Settlement::getSettlementNo, settlementNo));
        if (settlement == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结算单不存在");
        }
        if (!"PENDING".equals(settlement.getStatus())) {
            throw new BusinessException(ErrorCode.SETTLEMENT_EXISTS, "结算单已处理");
        }
        settlement.setStatus("PAID");
        settlement.setSettledAt(LocalDateTime.now());
        settlementMapper.updateById(settlement);
        if (auditService != null) {
            auditService.record("ADMIN", currentUserId(), "SETTLEMENT_CONFIRM", "SETTLEMENT",
                    settlementNo, Map.of("status", "PENDING"), Map.of("status", "PAID"));
        }
        Shop shop = shopMapper.selectById(settlement.getMerchantId());
        Long count = merchantOrderMapper.selectCount(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getMerchantId, settlement.getMerchantId())
                .eq(MerchantOrder::getSettled, 1));
        return toView(settlement, shop, count == null ? 0 : count.intValue());
    }

    private List<SettlementView> enrich(List<Settlement> settlements) {
        if (settlements.isEmpty()) {
            return List.of();
        }
        Map<Long, Shop> shops = shopMapper.selectBatchIds(
                        settlements.stream().map(Settlement::getMerchantId).distinct().toList()).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        return settlements.stream().map(s -> {
            Long count = itemMapper.selectCount(new LambdaQueryWrapper<SettlementItem>()
                    .eq(SettlementItem::getSettlementId, s.getId()));
            return toView(s, shops.get(s.getMerchantId()), count == null ? 0 : count.intValue());
        }).toList();
    }

    private SettlementView toView(Settlement s, Shop shop, int orderCount) {
        return new SettlementView(s.getSettlementNo(), s.getMerchantId(),
                shop == null ? null : shop.getShopName(), s.getPeriodStart(), s.getPeriodEnd(),
                s.getGrossAmountCent(), s.getCommissionCent(), s.getRefundCent(),
                nvl(s.getPenaltyCent()), s.getNetAmountCent(),
                s.getStatus(), orderCount, s.getSettledAt(), s.getCreatedAt());
    }

    /** 结算单详情：表头 + 每个子单一行，退款与赔付都可下钻核对。 */
    public SettlementDetailView detail(String settlementNo) {
        Settlement settlement = settlementMapper.selectOne(new LambdaQueryWrapper<Settlement>()
                .eq(Settlement::getSettlementNo, settlementNo));
        if (settlement == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结算单不存在");
        }
        if (!canAccess(settlement.getMerchantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        Shop shop = shopMapper.selectById(settlement.getMerchantId());
        List<SettlementItem> items = itemMapper.selectList(new LambdaQueryWrapper<SettlementItem>()
                .eq(SettlementItem::getSettlementId, settlement.getId())
                .orderByAsc(SettlementItem::getId));
        Map<Long, String> subOrderNos = items.isEmpty() ? Map.of()
                : merchantOrderMapper.selectBatchIds(
                                items.stream().map(SettlementItem::getMerchantOrderId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(MerchantOrder::getId, MerchantOrder::getSubOrderNo));
        List<SettlementItemView> views = items.stream()
                .map(item -> {
                    List<RefundLineView> refunds = refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                                    .eq(Refund::getMerchantOrderId, item.getMerchantOrderId())
                                    .eq(Refund::getStatus, "REFUNDED")
                                    .orderByAsc(Refund::getId))
                            .stream()
                            .map(refund -> new RefundLineView(refund.getRefundNo(),
                                    nvl(refund.getAmountCent()), refund.getReason(), refund.getFinishedAt()))
                            .toList();
                    List<PenaltyLineView> penalties = penaltyMapper.selectList(new LambdaQueryWrapper<Penalty>()
                                    .eq(Penalty::getMerchantOrderId, item.getMerchantOrderId())
                                    .eq(Penalty::getStatus, "SETTLED")
                                    .orderByAsc(Penalty::getDaysOverdue))
                            .stream()
                            .map(penalty -> new PenaltyLineView(penalty.getPenaltyNo(),
                                    nvl(penalty.getDaysOverdue()), nvl(penalty.getAmountCent()),
                                    penalty.getCreatedAt()))
                            .toList();
                    return new SettlementItemView(
                            subOrderNos.getOrDefault(item.getMerchantOrderId(), ""),
                            nvl(item.getGrossAmountCent()), nvl(item.getCommissionCent()),
                            nvl(item.getRefundCent()), nvl(item.getPenaltyCent()),
                            nvl(item.getNetAmountCent()), refunds, penalties);
                })
                .toList();
        return new SettlementDetailView(toView(settlement, shop, items.size()), views);
    }

    private boolean canAccess(Long merchantId) {
        Long userId = currentUserId();
        Role admin = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "ADMIN"));
        if (admin != null) {
            Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, userId)
                    .eq(UserRole::getRoleId, admin.getId()));
            if (count != null && count > 0) {
                return true;
            }
        }
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, userId));
        return shop != null && shop.getId().equals(merchantId);
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private Shop currentShop() {
        Long userId = currentUserId();
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, userId));
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_APPROVED, "没有开通店铺");
        }
        return shop;
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
