package com.atguigu.gulimall.product.dao;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品三级分类
 * 
 * @author wuxiaoqing
 * @email 960876643@qq.com
 * @date 2020-05-28 21:33:04
 */
@Mapper
public interface CategoryDao extends BaseMapper<CategoryEntity> {
	
}
