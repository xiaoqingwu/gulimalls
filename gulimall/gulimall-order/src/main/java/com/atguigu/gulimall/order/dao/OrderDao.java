package com.atguigu.gulimall.order.dao;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单
 * 
 * @author wuxiaoqing
 * @email 960876643@qq.com
 * @date 2020-05-24 15:04:57
 */
@Mapper
public interface OrderDao extends BaseMapper<OrderEntity> {
	
}
