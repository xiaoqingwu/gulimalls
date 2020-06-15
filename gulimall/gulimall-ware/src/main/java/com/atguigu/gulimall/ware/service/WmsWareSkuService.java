package com.atguigu.gulimall.ware.service;

import com.atguigu.common.to.SkuHasStockVO;
import com.atguigu.common.utils.PageUtils;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gulimall.ware.entity.WmsWareSkuEntity;

import java.util.List;
import java.util.Map;

/**
 * 商品库存
 *
 * @author wuxiaoqing
 * @email 960876643@qq.com
 * @date 2020-05-24 15:10:49
 */
public interface WmsWareSkuService extends IService<WmsWareSkuEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void addStack(Long skuId, Long wareId, Integer skuNum);

    List<SkuHasStockVO> getSkuHasStock(List<Long> skuIds);
}

