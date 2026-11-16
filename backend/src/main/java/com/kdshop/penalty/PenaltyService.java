package com.kdshop.penalty;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.penalty.entity.Penalty;
import com.kdshop.penalty.mapper.PenaltyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工期超时赔付（ISSUE-36 / ADR-0008）。
 *
 * <p>按天扫「已过承诺发货时间且未发货」的子单：每超 1 天赔该子单定金份额的 5%，累计上限 50%。
 * 同一子单同一天只有一条记录（`uk_penalties_mo_day` 兜底），漏跑可补；赔付产生后不因最终发货而撤回。
 * 赔付只在结算时从商家应结金额里扣减，首版不向顾客钱包打款。</p>
 */
@Service
public class PenaltyService {

    private static final Logger log = LoggerFactory.getLogger(PenaltyService.class);

    /** 每天赔付定金份额的 5%（基点）。 */
    static final int DAILY_RATE_BP = 500;
    /** 累计上限为定金份额的 50%（基点）。 */
    static final int CAP_RATE_BP = 5000;

    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderMapper orderMapper;
    private final PenaltyMapper penaltyMapper;

    public PenaltyService(MerchantOrderMapper merchantOrderMapper, OrderMapper orderMapper,
                          PenaltyMapper penaltyMapper) {
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderMapper = orderMapper;
        this.penaltyMapper = penaltyMapper;
    }

    @Scheduled(fixedDelayString = "${kdshop.jobs.scan-interval-ms:60000}", initialDelay = 45000)
    public void scan() {
        int created = generate();
        if (created > 0) {
            log.info("penalty job: created={}", created);
        }
    }

    public int generate() {
        LocalDateTime now = LocalDateTime.now();
        List<MerchantOrder> subs = merchantOrderMapper.selectList(new LambdaQueryWrapper<MerchantOrder>()
                .in(MerchantOrder::getStatus, List.of("PRODUCING", "PENDING_SHIPMENT"))
                .isNotNull(MerchantOrder::getPromisedShipAt)
                .lt(MerchantOrder::getPromisedShipAt, now)
                .orderByAsc(MerchantOrder::getPromisedShipAt)
                .last("LIMIT 50"));
        if (subs.isEmpty()) {
            return 0;
        }
        Map<Long, Order> orders = orderMapper.selectBatchIds(
                        subs.stream().map(MerchantOrder::getOrderId).distinct().toList()).stream()
                .collect(Collectors.toMap(Order::getId, order -> order));
        int created = 0;
        for (MerchantOrder sub : subs) {
            if (sub.getShippedAt() != null) {
                continue;
            }
            Order order = orders.get(sub.getOrderId());
            if (order == null) {
                continue;
            }
            created += generateFor(order, sub, now);
        }
        return created;
    }

    /** 为单个子单补齐从「上次已记天数 + 1」到当前逾期天数的赔付，直到封顶。 */
    int generateFor(Order order, MerchantOrder sub, LocalDateTime now) {
        int base = depositBase(order, sub);
        if (base <= 0) {
            return 0;
        }
        int cap = (int) Math.round(base * CAP_RATE_BP / 10000.0);
        int daily = Math.max(1, (int) Math.round(base * DAILY_RATE_BP / 10000.0));
        if (cap < 1) {
            return 0;
        }

        List<Penalty> existing = penaltyMapper.selectList(new LambdaQueryWrapper<Penalty>()
                .eq(Penalty::getMerchantOrderId, sub.getId()));
        int recorded = existing.stream()
                .mapToInt(p -> p.getAmountCent() == null ? 0 : p.getAmountCent())
                .sum();
        int maxDay = existing.stream()
                .mapToInt(p -> p.getDaysOverdue() == null ? 0 : p.getDaysOverdue())
                .max().orElse(0);
        long overdueDays = ChronoUnit.DAYS.between(sub.getPromisedShipAt(), now);
        if (overdueDays <= maxDay) {
            return 0;
        }

        int created = 0;
        for (long day = maxDay + 1; day <= overdueDays; day++) {
            int remaining = cap - recorded;
            if (remaining <= 0) {
                break;
            }
            Penalty penalty = new Penalty();
            penalty.setPenaltyNo("PN" + sub.getId() + "-" + day);
            penalty.setMerchantOrderId(sub.getId());
            penalty.setOrderId(sub.getOrderId());
            penalty.setMerchantId(sub.getMerchantId());
            penalty.setDaysOverdue((int) day);
            penalty.setAmountCent(Math.min(daily, remaining));
            penalty.setStatus("PENDING");
            try {
                penaltyMapper.insert(penalty);
            } catch (DuplicateKeyException e) {
                continue;
            }
            recorded += penalty.getAmountCent();
            created++;
        }
        return created;
    }

    /** 子单应分摊的定金：按子单实付占主单应付的比例拆主单定金。 */
    int depositBase(Order order, MerchantOrder sub) {
        int deposit = order.getDepositCent() == null ? 0 : order.getDepositCent();
        int payTotal = order.getPayAmountCent() == null ? 0 : order.getPayAmountCent();
        int subPay = sub.getPayAmountCent() == null ? 0 : sub.getPayAmountCent();
        if (deposit <= 0 || payTotal <= 0 || subPay <= 0) {
            return 0;
        }
        return (int) Math.round((double) deposit * subPay / payTotal);
    }
}
