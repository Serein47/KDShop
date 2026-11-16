package com.kdshop.settlement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.settlement.entity.Settlement;
import com.kdshop.settlement.entity.SettlementItem;
import com.kdshop.settlement.mapper.SettlementItemMapper;
import com.kdshop.settlement.mapper.SettlementMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 结算对账（ISSUE-40）：校验结算单表头与明细之和，以及每行
 * `应结 = 成交 - 佣金 - 退款 - 赔付`，差异写 ERROR 日志告警。
 */
@Service
public class SettlementReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(SettlementReconcileJob.class);

    private final SettlementMapper settlementMapper;
    private final SettlementItemMapper itemMapper;

    public SettlementReconcileJob(SettlementMapper settlementMapper, SettlementItemMapper itemMapper) {
        this.settlementMapper = settlementMapper;
        this.itemMapper = itemMapper;
    }

    @Scheduled(cron = "${kdshop.jobs.settlement-reconcile-cron:0 30 3 * * *}")
    public void scan() {
        int mismatches = reconcile();
        if (mismatches > 0) {
            log.error("结算对账发现 {} 张结算单存在差异，请检查", mismatches);
        }
    }

    public int reconcile() {
        List<Settlement> settlements = settlementMapper.selectList(new LambdaQueryWrapper<Settlement>()
                .in(Settlement::getStatus, List.of("PENDING", "PAID"))
                .last("LIMIT 200"));
        int mismatches = 0;
        for (Settlement settlement : settlements) {
            List<SettlementItem> items = itemMapper.selectList(new LambdaQueryWrapper<SettlementItem>()
                    .eq(SettlementItem::getSettlementId, settlement.getId()));
            int gross = 0;
            int commission = 0;
            int refund = 0;
            int penalty = 0;
            int net = 0;
            boolean bad = false;
            for (SettlementItem item : items) {
                int itemNet = nvl(item.getNetAmountCent());
                int expected = nvl(item.getGrossAmountCent()) - nvl(item.getCommissionCent())
                        - nvl(item.getRefundCent()) - nvl(item.getPenaltyCent());
                if (itemNet != expected) {
                    bad = true;
                    log.error("结算明细对账差异：settlement={} sub={} net={} expected={}",
                            settlement.getSettlementNo(), item.getMerchantOrderId(), itemNet, expected);
                }
                gross += nvl(item.getGrossAmountCent());
                commission += nvl(item.getCommissionCent());
                refund += nvl(item.getRefundCent());
                penalty += nvl(item.getPenaltyCent());
                net += itemNet;
            }
            if (gross != nvl(settlement.getGrossAmountCent())
                    || commission != nvl(settlement.getCommissionCent())
                    || refund != nvl(settlement.getRefundCent())
                    || penalty != nvl(settlement.getPenaltyCent())
                    || net != nvl(settlement.getNetAmountCent())) {
                bad = true;
                log.error("结算表头对账差异：settlement={} 明细合计(gross={},commission={},refund={},penalty={},net={})"
                                + " 表头(gross={},commission={},refund={},penalty={},net={})",
                        settlement.getSettlementNo(), gross, commission, refund, penalty, net,
                        settlement.getGrossAmountCent(), settlement.getCommissionCent(),
                        settlement.getRefundCent(), settlement.getPenaltyCent(),
                        settlement.getNetAmountCent());
            }
            if (bad) {
                mismatches++;
            }
        }
        return mismatches;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
