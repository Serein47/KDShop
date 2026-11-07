package com.kdshop.settlement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    @Autowired(required = false)
    private AuditService auditService;

    public SettlementService(SettlementMapper settlementMapper, SettlementItemMapper itemMapper,
                             MerchantOrderMapper merchantOrderMapper, ShopMapper shopMapper,
                             UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.settlementMapper = settlementMapper;
        this.itemMapper = itemMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.shopMapper = shopMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
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
                    .ge(MerchantOrder::getCompletedAt, from)
                    .lt(MerchantOrder::getCompletedAt, to));
            if (subs.isEmpty()) {
                continue;
            }

            int rate = shop.getCommissionRate() == null ? 500 : shop.getCommissionRate();
            int gross = 0;
            int commission = 0;
            int refund = 0;
            for (MerchantOrder sub : subs) {
                int pay = sub.getPayAmountCent() == null ? 0 : sub.getPayAmountCent();
                int refunded = sub.getRefundedAmountCent() == null ? 0 : sub.getRefundedAmountCent();
                gross += pay;
                refund += refunded;
                commission += (int) Math.round((pay - refunded) * rate / 10000.0);
            }
            int net = gross - refund - commission;

            Settlement settlement = new Settlement();
            settlement.setSettlementNo("ST" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + String.format("%03d", shop.getId()));
            settlement.setMerchantId(shop.getId());
            settlement.setPeriodStart(start);
            settlement.setPeriodEnd(end);
            settlement.setGrossAmountCent(gross);
            settlement.setCommissionCent(commission);
            settlement.setRefundCent(refund);
            settlement.setNetAmountCent(net);
            settlement.setStatus("PENDING");
            settlementMapper.insert(settlement);

            for (MerchantOrder sub : subs) {
                int pay = sub.getPayAmountCent() == null ? 0 : sub.getPayAmountCent();
                int refunded = sub.getRefundedAmountCent() == null ? 0 : sub.getRefundedAmountCent();
                int subCommission = (int) Math.round((pay - refunded) * rate / 10000.0);
                SettlementItem item = new SettlementItem();
                item.setSettlementId(settlement.getId());
                item.setMerchantOrderId(sub.getId());
                item.setGrossAmountCent(pay);
                item.setCommissionCent(subCommission);
                item.setNetAmountCent(pay - refunded - subCommission);
                itemMapper.insert(item);

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
                s.getGrossAmountCent(), s.getCommissionCent(), s.getRefundCent(), s.getNetAmountCent(),
                s.getStatus(), orderCount, s.getSettledAt(), s.getCreatedAt());
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
