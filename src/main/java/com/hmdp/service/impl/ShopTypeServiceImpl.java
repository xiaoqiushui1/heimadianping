package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
   private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopTypeService typeService;
    @Override
    public Result orderbylist() {//店铺商单redis缓存
        String key=CACHE_SHOP_KEY;
        //1.查询redis是否有缓存
    List<String> shopTypes = stringRedisTemplate.opsForList().range(key,0, -1);//查询redis中的list集合
        List<ShopType> shopTypes1=new ArrayList<>();
    if (CollectionUtil.isNotEmpty(shopTypes)){
        //2.有缓存，直接返回
     for (String shopType4 : shopTypes) {
         ShopType shopType8= JSONUtil.toBean(shopType4,ShopType.class);//将list字符串集合转为ShopType对象
         shopTypes1.add(shopType8);
     }
         log.info("查询redis结果："+shopTypes1);
        return Result.ok(shopTypes1);
    }
    //3.无缓存，查询数据库
        List<ShopType> shopTypes2 =query().orderByAsc("sort").list();//查询数据库（Mybatisplus的方法）
        //4.若为空，返回错误
            if(CollectionUtil.isEmpty(shopTypes2)){
                return Result.fail("无店铺类型");
            }
            //5.不为空，写入redis
            for (ShopType shopType : shopTypes2) {
                stringRedisTemplate.opsForList().rightPush(key,JSONUtil.toJsonStr(shopType));
            }

        return Result.ok(shopTypes2);
    }
}
