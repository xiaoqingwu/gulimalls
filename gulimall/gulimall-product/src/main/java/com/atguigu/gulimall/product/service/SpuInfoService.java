package com.atguigu.gulimall.product.service;

import com.atguigu.gulimall.product.vo.SpuSaveVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.entity.SpuInfoEntity;

import java.util.Map;

/**
 * spu信息
 *
 * @author wuxiaoqing
 * @email 960876643@qq.com
 * @date 2020-05-28 21:33:04
 */
public interface SpuInfoService extends IService<SpuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void saveBaseSpuInfo(SpuInfoEntity infoEntity);

    void saveSpuInfo(SpuSaveVo vo);

    PageUtils queryPageByCondition(Map<String, Object> params);

    void up(Long spuId);
}

