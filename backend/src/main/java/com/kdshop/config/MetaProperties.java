package com.kdshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 前端启动配置：货币、时区、运费与各类时限。 */
@ConfigurationProperties(prefix = "kdshop.meta")
public record MetaProperties(
        String currency,
        String timezone,
        int shippingFreeThresholdCent,
        int shippingFeeCent,
        int payTimeoutMinutes,
        int autoConfirmDays,
        int settlementProtectionDays,
        /** 预售/定制订单支付定金的时限（小时）；普通订单用 payTimeoutMinutes */
        int preorderPayTimeoutHours,
        /** 承诺发货前多少天发尾款通知 */
        int balanceNotifyDaysBefore,
        /** 尾款通知后多少天未付则关闭订单 */
        int balancePayDays,
        /** 子单完成后多少天内可申请售后 */
        int afterSalesWindowDays,
        /** 商家审核售后的时限（小时），超时自动裁决 */
        int afterSalesReviewHours,
        /** 退货退款：顾客多少天内未填寄回物流则关闭 */
        int afterSalesReturnDays,
        /** 商家收货后多少小时内提交质检，超时按良品处理 */
        int afterSalesInspectHours
) {
}
