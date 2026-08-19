package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
//先查redis后查数据库，若redis没有，mysql有就在redis中写入，增加查询速率。
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        String Key= CACHE_SHOP_KEY+id;//创建redis的key
        //1.从redis中查询
       String shopJson =stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);//查询回来的为String字符串
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
        //3.存在，则返回
        Shop shop=    JSONUtil.toBean(shopJson,Shop.class);//这是将字符串转为对象，若是对象转对象用 UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);//将user对象转为UserDTO对象
return Result.ok(shop);}
        //判断命中的是否是空值
        if ( shopJson != null){
            return Result.fail("店铺不存在");
        }
        //4.不存在，则根据id查询数据库
            Shop shop = this.getById(id);//java对象
        if ( shop == null){
            //将空值写入redis，防止缓存穿透
            stringRedisTemplate.opsForValue().set(Key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //5.数据库不存在，则返回错误
            return Result.fail("店铺不存在");
        }
        //6.数据库存在，则写入redis，后返回。添加随机时间防止缓存雪崩.使用ThreadLocalRandom，不要用Random，多线程环境性能更好。需要导入java.util.concurrent.ThreadLocalRandom
       stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop),LOGIN_USER_TTL+ ThreadLocalRandom.current().nextLong(1,6), TimeUnit.MINUTES);//将shop对象转为json字符串存入redis中，期限30分钟保证缓存一致性。

        return Result.ok(shop);
    }
    @Transactional//事务回滚，保证acid 特性
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不能为空");
        }
    //1.更新数据库
        updateById( shop);
        //2.删除redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }
}
