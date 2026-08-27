package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {//创建id生成器
    private StringRedisTemplate stringRedisTemplate;
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
private static final long BEGIN_TIMESTAMP = 1640995200L;//从1970-01-01 00:00:00开始到2022-01-01 00:00:00的秒数
private static final int COUNT_BITS = 32;//序列号位数
    public long nextId(String keyPrefix) {
        //1.生成时间戳
         LocalDateTime now = LocalDateTime.now();
         long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
         long timestamp = nowSecond - BEGIN_TIMESTAMP;// 获取当前时间戳
        //2.生成序列号
        //2.1.获取当前日期，精确到天//每天一个id
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2.自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3.拼接返回
        return timestamp << COUNT_BITS | count;//左移运算符，将timestamp左移32位，将count拼接到timestamp后面
    }








}

