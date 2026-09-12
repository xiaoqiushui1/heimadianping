package com.hmdp.utils;
import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
public class simpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    public simpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    private static  final  String KEY_PREFIX = "lock:";
private static final  String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
private static final DefaultRedisScript<Long> UNLOCK_SCRIPT ;
static {
    UNLOCK_SCRIPT = new DefaultRedisScript<>();
    UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    UNLOCK_SCRIPT.setResultType(Long.class);
}
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程表示
       // long threadId = Thread.currentThread().getId();(由于j不同jvm之间维护不同的线程id表，可能造成线程标识一致导致误删锁)，从而使用UUID来代替
        String threadId=ID_PREFIX+Thread.currentThread().getId();
        Boolean success=stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);//  BooleanUtil.isTrue(success)
    }
//    @Override
//    public void unLock() {
//        //获取线程标识
//        String threadId=ID_PREFIX+Thread.currentThread().getId();//示例：671d64c0ebb84883b87797846648ef77-431
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);//看看缓存中是否存有这个锁
//        if(threadId.equals(id)) {
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);(这个到最后可能会触发垃圾回收机制，从而导致锁的事务一致性失效（原子性失效）。)
//        }
//基于lua脚本释放锁
    @Override
    public void unLock() {
     //调用lua脚本
  stringRedisTemplate.execute(UNLOCK_SCRIPT,
        Collections.singletonList(KEY_PREFIX + name),//生成单一集合。集合中只能有一个元素
        ID_PREFIX+Thread.currentThread().getId());//直接一行代码进行实现，保证原子性
    }
    }
