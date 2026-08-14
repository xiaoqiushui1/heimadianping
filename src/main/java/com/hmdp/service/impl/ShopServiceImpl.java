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

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        //4.不存在，则根据id查询数据库
            Shop shop = this.getById(id);//java对象
        if ( shop == null){
            //5.数据库不存在，则返回错误
            return Result.fail("店铺不存在");
        }
        //6.数据库存在，则写入redis，后返回。
       stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop));//将shop对象转为json字符串

        return Result.ok(shop);
    }
}
