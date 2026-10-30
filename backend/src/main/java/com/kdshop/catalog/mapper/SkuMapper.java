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
}
