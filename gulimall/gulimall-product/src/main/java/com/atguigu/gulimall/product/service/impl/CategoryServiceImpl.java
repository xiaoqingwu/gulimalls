package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catelog2VO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.*;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redisson;

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

    @Override
    public Long[] findCatelogPath(Long catelogId) {
        List<Long> path = new ArrayList<>();
        List<Long> parentPath = findParentPath(catelogId, path);
        Collections.reverse(parentPath);
        return parentPath.toArray(new Long[parentPath.size()]);
    }
    private List<Long> findParentPath(Long catelogId,List<Long> paths){
        paths.add(catelogId);
        CategoryEntity byId = this.getById(catelogId);
        if(byId.getParentCid() !=0){
            findParentPath(byId.getParentCid(),paths);
        }
        return paths;
    }
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        categoryBrandRelationService.updateCategory(category.getCatId(),category.getName());
    }

    @Override
    public List<CategoryEntity> getLevel1Categories() {
        return baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));
    }

    @Override
    public Map<String, List<Catelog2VO>> getCatalogJson() {
        List<CategoryEntity> selectList = baseMapper.selectList(null);
        List<CategoryEntity> level1Categories = getParent_cid(selectList,0L);
        Map<String, List<Catelog2VO>> parentCid =  level1Categories.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
            List<Catelog2VO> catelog2VOS = null;
            if(!CollectionUtils.isEmpty(categoryEntities)){
                catelog2VOS = categoryEntities.stream().map(l2->{
                    Catelog2VO catelog2VO = new Catelog2VO(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    List<CategoryEntity> level3Catalog = getParent_cid(selectList, l2.getCatId());
                    if(!CollectionUtils.isEmpty(level3Catalog)){
                        List<Catelog2VO.Catelog3VO> collect = level3Catalog.stream().map(l3 -> {
                            Catelog2VO.Catelog3VO catelog3VO = new Catelog2VO.Catelog3VO(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3VO;
                        }).collect(Collectors.toList());
                        catelog2VO.setCatalog3List(collect);
                    }
                    return catelog2VO;
                }).collect(Collectors.toList());
            }
            return catelog2VOS;
        }));
        return parentCid;
    }
    private List<CategoryEntity> getParent_cid(List<CategoryEntity> selectList, Long parentCid) {
        return selectList.stream().filter(o->o.getParentCid().equals(parentCid)).collect(Collectors.toList());
    }

    public Map<String, List<Catelog2VO>> getCatalogJsonFromDBWithRedissonLock() {
        RLock lock = redisson.getLock("catalogJson-lock");
        lock.lock();
        Map<String, List<Catelog2VO>> dataFromDB;
        try {
            dataFromDB = getDataFromDB();
        } finally {
            lock.unlock();
        }
        return dataFromDB;
    }

        /**
         * 利用Redis进行缓存商品分类数据
         *
         * @return
         */
    public Map<String, List<Catelog2VO>> getCatalogJson2() throws InterruptedException {
        // TODO 产生堆外内存溢出 OutOfDirectMemoryError
        /**
         * 1. SpringBoot2.0之后默认使用 lettuce 作为操作 redis 的客户端，lettuce 使用 Netty 进行网络通信
         * 2. lettuce 的 bug 导致 Netty 堆外内存溢出 -Xmx300m   Netty 如果没有指定对外内存 默认使用 JVM 设置的参数
         *      可以通过 -Dio.netty.maxDirectMemory 设置堆外内存
         * 解决方案：不能仅仅使用 -Dio.netty.maxDirectMemory 去调大堆外内存
         *      1. 升级 lettuce 客户端   2. 切换使用 jedis
         *
         *      RedisTemplate 对 lettuce 与 jedis 均进行了封装 所以直接使用 详情见：RedisAutoConfiguration 类
         */

        /**
         * - 空结果缓存：解决缓存穿透
         *
         * - 设置过期时间（加随机值）：解决缓存雪崩
         *
         * - 加锁：解决缓存击穿
         */
        // 给缓存中放入JSON字符串，取出JSON字符串还需要逆转为能用的对象类型

        // 1. 加入缓存逻辑， 缓存中存的数据是 JSON 字符串
        String catelogJSON = stringRedisTemplate.opsForValue().get("catelogJSON");
        if (!StringUtils.isEmpty(catelogJSON)){
            // 2 如果缓存未命中 则查询数据库
            Map<String, List<Catelog2VO>> catalogJsonFromDBWithRedisLock = getCatalogJsonFromDBWithRedisLock();
            return catalogJsonFromDBWithRedisLock;
        }
        Map<String, List<Catelog2VO>> result = JSON.parseObject(catelogJSON, new TypeReference<Map<String, List<Catelog2VO>>>() {
        });
        return result;
    }

    private Map<String, List<Catelog2VO>> getDataFromDB() {
        String catelogJSON = stringRedisTemplate.opsForValue().get("catelogJSON");
        if (!StringUtils.isEmpty(catelogJSON)){
            Map<String, List<Catelog2VO>> result = JSON.parseObject(catelogJSON, new TypeReference<Map<String, List<Catelog2VO>>>() {
            });
            return result;
        }
        List<CategoryEntity> selectList = baseMapper.selectList(null);
        List<CategoryEntity> level1Categories = getParent_cid(selectList,0L);
        Map<String, List<Catelog2VO>> parentCid =  level1Categories.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
            List<Catelog2VO> catelog2VOS = null;
            if(!CollectionUtils.isEmpty(categoryEntities)){
                catelog2VOS = categoryEntities.stream().map(l2->{
                    Catelog2VO catelog2VO = new Catelog2VO(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    List<CategoryEntity> level3Catalog = getParent_cid(selectList, l2.getCatId());
                    if(!CollectionUtils.isEmpty(level3Catalog)){
                        List<Catelog2VO.Catelog3VO> collect = level3Catalog.stream().map(l3 -> {
                            Catelog2VO.Catelog3VO catelog3VO = new Catelog2VO.Catelog3VO(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3VO;
                        }).collect(Collectors.toList());
                        catelog2VO.setCatalog3List(collect);
                    }
                    return catelog2VO;
                }).collect(Collectors.toList());
            }
            return catelog2VOS;
        }));
    //查询数据放入缓存中  将对象转为json放入缓存中
        String cache   = JSON.toJSONString(parentCid);
        stringRedisTemplate.opsForValue().set("catelogJSON",cache,1, TimeUnit.DAYS);
        return parentCid;
    }

    /**
     * Redis 实现分布式锁
     *
     * @return
     */
    public Map<String, List<Catelog2VO>> getCatalogJsonFromDBWithRedisLock() throws InterruptedException {
        //1、redis占位
        String uuid = UUID.randomUUID().toString();
        Boolean lockResult = stringRedisTemplate.opsForValue().setIfAbsent("lock", uuid, 300, TimeUnit.SECONDS);
        if (lockResult){
            //2、加锁成功，执行业务
            Map<String, List<Catelog2VO>> dataFromDB;
            try {
                dataFromDB = getDataFromDB();
            }finally {
                String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                stringRedisTemplate.execute(new DefaultRedisScript<Long>(script,Long.class ),Collections.singletonList("lock"),uuid);
            }
            return dataFromDB;
        }else {
            // 3 加锁失败 睡眠 100ms 后重试
            Thread.sleep(100);
            return getCatalogJsonFromDBWithRedisLock();
        }

    }
}