package com.kdshop.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kdshop.catalog.entity.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    /** 条件更新锁库存：库存不足时返回 0，由调用方回滚。 */
    @Update("UPDATE skus SET locked_stock = locked_stock + #{qty} " +
            "WHERE id = #{id} AND stock - locked_stock >= #{qty}")
    int lockStock(@Param("id") Long id, @Param("qty") int qty);

    /** 支付成功：把锁定的库存转为实扣。 */
    @Update("UPDATE skus SET stock = stock - #{qty}, locked_stock = locked_stock - #{qty} " +
            "WHERE id = #{id} AND stock >= #{qty} AND locked_stock >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /** 取消订单：释放下单时锁定的库存，不改变可售库存。 */
    @Update("UPDATE skus SET locked_stock = locked_stock - #{qty} " +
            "WHERE id = #{id} AND locked_stock >= #{qty}")
    int unlockStock(@Param("id") Long id, @Param("qty") int qty);
}
