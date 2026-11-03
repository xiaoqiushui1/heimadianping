package com.hmdp.kafka;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
@KafkaListener(topics = "voucher-orders",groupId = "order-group")//选择消费的Kafka主题，以及初始消费者组
public class VoucherOrderConsumer {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RedissonClient redissonClient;

    @KafkaHandler
    public void handleVoucherOrder(VoucherOrder voucherOrder){
        // 1. 获取用户ID
        Long userId =  voucherOrder.getUserId();
        // 2. 创建分布式锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 3. 尝试获取锁
        boolean locked = lock.tryLock();
        if (!locked) {
            log.error("不允许重复下单！");
            return;
        }
        try {
            // 4. 处理订单创建逻辑
            voucherOrderService.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("处理订单异常", e);
        } finally {
            lock.unlock();
        }
    }
}
