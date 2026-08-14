package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
     void testRedis()
    {
        String key=CACHE_SHOP_KEY;
        List<String> shopTypes = stringRedisTemplate.opsForList().range(key, 0, -1);
        List<ShopType> shopTypes1=new ArrayList<>();
        for (String shopType4 : shopTypes) {
            ShopType shopType2= JSONUtil.toBean(shopType4,ShopType.class);//将list字符串集合转为ShopType对象
            shopTypes1.add(shopType2);
        }
        shopTypes1.sort((a, b) -> a.getSort() - b.getSort());
        System.out.println(shopTypes1);

    }
}