package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import com.hmdp.utils.RedisData;
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
      //缓存穿透
        //Shop shop  = queryByIdWithThrogh(id);
        //互斥锁解决缓存击穿
        //Shop shop = queryByIdWithMutex(id);
        //用逻辑过期解决缓存击穿
        Shop shop = queryByIdWithLogicExpire(id);
        if ( shop == null){
            return Result.fail("店铺不存在");
        }
        //7.返回
        return Result.ok(shop) ;
    }
//用逻辑过期解决缓存击穿(不用考虑缓存穿透的问题)因为写入redis的ttl为永久（-1），逻辑过期时间是RedisData+expireSeconds
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//创建线程池,线程池大小为10
    public Shop queryByIdWithLogicExpire(Long id) {
        String Key= CACHE_SHOP_KEY+id;//创建redis的key
        //1.从redis中查询
        String shopJson =stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);//查询回来的为String字符串
        //2.判断是否存在
        if(StrUtil.isBlank(shopJson)){
         //3.不存在，直接返回空.
            return null;
        }
        //4.命中，需要下吧json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data =(JSONObject) redisData.getData();//返回的是JSONObject对象
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1 未过期，则直接店铺信息
            return shop;
        }
        //5.2 过期，需要缓存重建
        //6.缓存重建
        //6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //6.2 判断是否获取锁成功
        if (isLock) {//大概会获取锁的线程
            //6.3成功，则开始独立线程，运行重建缓存
         CACHE_REBUILD_EXECUTOR.submit(() -> {
             try {
                 //重建缓存
                 this.saveShop2Redis(id,CACHE_SHOP_TTL);//设计逻辑时间30分钟
             } catch (Exception e) {
                 throw new RuntimeException(e);
             }finally {
                 //释放锁
                 unLock(lockKey);
             }
            });
        }
        //6.4 返回逻辑上已经过期的店铺信息
        return shop;

    }







    //用互斥锁解决缓存击穿
//    public Shop queryByIdWithMutex(Long id) {
//        String Key= CACHE_SHOP_KEY+id;//创建redis的key
//        //1.从redis中查询
//        String shopJson =stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);//查询回来的为String字符串
//        //2.判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3.存在，则返回
//            Shop shop=    JSONUtil.toBean(shopJson,Shop.class);//这是将字符串转为对象，若是对象转对象用 UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);//将user对象转为UserDTO对象
//            return shop;
//        }
//        //判断命中的是否是空值
//        if ( shopJson != null){//缓存穿透解决空值问题,这里面缓存过期或者不一致，则返回空
//            return null;
//        }
//        //4.实现缓存重建
//        //4.1 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY+id;
//        Shop shop = null;//java对象
//        try {
//            boolean isLock = tryLock(lockKey);
//            //4.2 判断是否获取锁成功
//            if (!isLock) {
//                //4.3 失败，则该线程休眠并重试
//                Thread.sleep(50);
//                return queryByIdWithMutex(id);//递归调用，要么获取redis的重写缓存，要么获取锁.
//            }
//            //4.4成功，则根据id查询数据库
//            shop = this.getById(id);
//            if ( shop == null){
//                //将空值写入redis，防止缓存穿透
//                stringRedisTemplate.opsForValue().set(Key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                //5.数据库不存在，则返回错误
//                return null;
//            }
//            //6.数据库存在，则写入redis，后返回。
//            stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop),LOGIN_USER_TTL, TimeUnit.MINUTES);//将shop对象转为json字符串存入redis中，期限30分钟保证缓存一致性。
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            //7.释放互斥锁
//            unLock(lockKey);
//        }
//       //8.返回
//        return shop;
//   }






// 缓存穿透具体代码   public Shop queryByIdWithThrogh(Long id) {
//        String Key= CACHE_SHOP_KEY+id;//创建redis的key
//        //1.从redis中查询
//        String shopJson =stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);//查询回来的为String字符串
//        //2.判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3.存在，则返回
//            Shop shop=    JSONUtil.toBean(shopJson,Shop.class);//这是将字符串转为对象，若是对象转对象用 UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);//将user对象转为UserDTO对象
//            return shop;
//        }
//        //判断命中的是否是空值
//        if ( shopJson != null){//缓存穿透解决空值问题,这里面缓存过期或者不一致，则返回空
//            return null;
//        }
//        //4.不存在，则根据id查询数据库
//        Shop shop = this.getById(id);//java对象
//        if ( shop == null){
//            //将空值写入redis，防止缓存穿透
//            stringRedisTemplate.opsForValue().set(Key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            //5.数据库不存在，则返回错误
//            return null;
//        }
//        //6.数据库存在，则写入redis，后返回。添加随机时间防止缓存雪崩.使用ThreadLocalRandom，不要用Random，多线程环境性能更好。需要导入java.util.concurrent.ThreadLocalRandom
//        stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop),LOGIN_USER_TTL+ ThreadLocalRandom.current().nextLong(1,6), TimeUnit.MINUTES);//将shop对象转为json字符串存入redis中，期限30分钟保证缓存一致性。
//
//        return shop;
//    }
    //实现互斥锁
    //1.尝试获取锁,用的是redis的String数据结构的setnx命令
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1");//这个Boolean是包装类
        return BooleanUtil.isTrue(flag);//需要把flag转为基本类型,进行判断flag是否为true,直接返回可能会报null，自动拆箱.
    }
    //2.释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
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
    //缓存预热
    public void saveShop2Redis(Long id,Long expiresSeconds){
        //1.查询店铺数据
        Shop shop = this.getById(id);
        //2.封装逻辑过期时间
      RedisData redisData=new RedisData();
      redisData.setData(shop);
      redisData.setExpireTime(LocalDateTime.now().plusSeconds(expiresSeconds));
      //3.写入redis
      stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));

    }
}
