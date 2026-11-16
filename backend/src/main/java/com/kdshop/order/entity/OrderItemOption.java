package com.kdshop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 订单行定制项快照：下单时的选项名、值与加价，后续选项改动不影响历史订单。 */
@Data
@TableName("order_item_options")
public class OrderItemOption {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderItemId;
    private Long optionId;
    private String optionName;
    private String valueText;
    private Integer priceDeltaCent;
}
