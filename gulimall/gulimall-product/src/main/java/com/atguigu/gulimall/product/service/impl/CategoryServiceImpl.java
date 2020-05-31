package com.atguigu.gulimall.product.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.*;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryEntity> listWithTree() {
        //查询所有分类
        List<CategoryEntity>entities = baseMapper.selectList(null);
        //2、组装成父子的树形结构
        //2.1）、找到所有的一级分类
        List<CategoryEntity> levelMenus = entities.stream().filter(categoryEntity -> categoryEntity.getParentCid() == 0).
                map(menu -> {menu.setChildren(getChildrens(menu,entities));
                return menu;
                }
                ).sorted((menu1,menu2) -> {
            return (menu1.getSort()==null? 0:menu1.getSort())-(menu2.getSort()==null?0:menu2.getSort());
        }).collect(Collectors.toList());
        return levelMenus;
    }
    //递归查找所有菜单的子菜单
    private List<CategoryEntity> getChildrens(CategoryEntity root,List<CategoryEntity> all){
        List<CategoryEntity> children = all.stream().filter(
               categoryEntity -> {return categoryEntity.getParentCid() == root.getCatId();
               }).map(categoryEntity -> {
                   categoryEntity.setChildren(getChildrens(categoryEntity,all));
                   return categoryEntity;
        }).sorted((menu1,menu2) -> {
            return (menu1.getSort()==null? 0:menu1.getSort())-(menu2.getSort()==null?0:menu2.getSort());
        }).collect(Collectors.toList());
        return children;
    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        //TODO  1、检查当前删除的菜单，是否被别的地方引用
        //逻辑删除
        baseMapper.deleteBatchIds(asList);
    }
}