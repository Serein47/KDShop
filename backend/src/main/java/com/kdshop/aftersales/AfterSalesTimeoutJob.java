package com.kdshop.aftersales;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.aftersales.entity.AfterSale;
import com.kdshop.aftersales.mapper.AfterSaleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后超时任务（ISSUE-38）：
 * 1. 退货退款 7 天未填寄回物流 → 关闭售后；
 * 2. 商家收货后 48 小时未质检 → 系统按良品处理并自动退款。
 * 单实例部署；多实例时加分布式锁。
 */
@Service
public class AfterSalesTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(AfterSalesTimeoutJob.class);

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSalesService afterSalesService;

    public AfterSalesTimeoutJob(AfterSaleMapper afterSaleMapper, AfterSalesService afterSalesService) {
        this.afterSaleMapper = afterSaleMapper;
        this.afterSalesService = afterSalesService;
    }

    @Scheduled(fixedDelayString = "${kdshop.jobs.scan-interval-ms:60000}", initialDelay = 50000)
    public void scan() {
        int closed = closeOverdueReturns();
        int inspected = autoInspectOverdue();
        if (closed > 0 || inspected > 0) {
            log.info("after-sales timeout job: closed={}, autoInspected={}", closed, inspected);
        }
    }

    public int closeOverdueReturns() {
        LocalDateTime now = LocalDateTime.now();
        List<AfterSale> rows = afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getStatus, "RETURNING")
                .isNull(AfterSale::getReturnTrackingNo)
                .isNotNull(AfterSale::getReturnDueAt)
                .lt(AfterSale::getReturnDueAt, now)
                .last("LIMIT 50"));
        int count = 0;
        for (AfterSale row : rows) {
            if (afterSalesService.closeOverdueReturn(row.getId())) {
                count++;
            }
        }
        return count;
    }

    public int autoInspectOverdue() {
        LocalDateTime now = LocalDateTime.now();
        List<AfterSale> rows = afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getStatus, "RECEIVED")
                .isNotNull(AfterSale::getInspectDueAt)
                .lt(AfterSale::getInspectDueAt, now)
                .last("LIMIT 50"));
        int count = 0;
        for (AfterSale row : rows) {
            if (afterSalesService.autoInspectGood(row.getId())) {
                count++;
            }
        }
        return count;
    }
}
