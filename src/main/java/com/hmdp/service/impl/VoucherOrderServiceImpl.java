package com.hmdp.service.impl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    //异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private IVoucherOrderService proxy;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //是在判断seckill.lua脚本之后进行获取的,所以这里获取的voucherorder信息已经封装了
                    // 1.获取订单中的队列消息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常:", e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1.获取用户
        Long userId = voucherOrder.getUserId();//因为是异步线程的操作，所以指南用voucherorder的方法获取用户id，这是在判断lua脚本之后进行的，voucherorder的信息以及封装了
        // 2.创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 3.尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 4.判断是否获得锁成功
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }
        try {
            //注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过一次！");
            return;
        }
        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            log.error("库存不足！");
            return;
        }
        // 7.创建订单
        save(voucherOrder);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本(注意在执行lua脚本之前要保证秒杀卷的库存信息已经提前写好到redis缓存中！)
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2.为0，有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.4.用户id
        voucherOrder.setUserId(userId);
        // 2.5.代金券id
        voucherOrder.setVoucherId(voucherId);
        // 2.6.放入阻塞队列
        orderTasks.add(voucherOrder);
        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 3.返回订单id
        return Result.ok(orderId);
    }
}
    //1.自己设计的分布式锁和redisson分布式锁的实现，重点！
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //优惠卷秒杀下单
//        //1.根据前端传递id查询数据库是否有此秒杀卷
//        SeckillVoucher voucherOrder = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始
//        if (voucherOrder.getBeginTime().isAfter(LocalDateTime.now())) {
//            //2.1尚未开始,则返回错误信息
//            return Result.fail("尚未开始！");
//        }
//        //2.1.2判断秒杀是否结束
//        if (voucherOrder.getEndTime().isBefore(LocalDateTime.now())) {
//            //秒杀已经结束
//            return Result.fail("秒杀已经结束");
//        }
//        //2.2判断库存是否充足
//        if (voucherOrder.getStock() < 1) {
//            //库存不足
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //给每个客户分配唯一锁,实现一人一单
//       // synchronized (userId.toString().intern()) {//锁对象控制的代码块,黑马一人一单的讲解底层(悲观锁实现一人一单)
//         //   IVoucherOrderService  proxy = (IVoucherOrderService) AopContext.currentProxy();//获取当前(spring生成的)代理对象，也就是接口类IVoucherOrderService的代理对象(接口类的作用之一)
//         //   return proxy.createVoucherOrder(voucherId);//createVoucherOrder(voucherId)默认是当前对象调用(this(也就是voucherOrderserviceImpl)),而当前对象调用会造成事务失效。
//        //}//给每个用户分配唯一锁(但是多集群下会造成同一进程获取一把锁的情况（jvm的不一致）)
//        //分布式锁
//        //simpleRedisLock simpleRedisLock = new simpleRedisLock("order:" + userId, stringRedisTemplate);//自动注入bean对象(service类)
//        //redisson 分布式锁对象
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean islock= lock.tryLock();
//   // boolean islock= simpleRedisLock.tryLock(1200);
//    if(!islock){
//        return Result.fail("请勿重复下单");
//    }
//        try {
//            IVoucherOrderService  proxy = (IVoucherOrderService) AopContext.currentProxy();//获取当前(spring生成的)代理对象，也就是接口类IVoucherOrderService的代理对象(接口类的作用之一)
//            return proxy.createVoucherOrder(voucherId);//createVoucherOrder(voucherId)
//        } finally {
//          //  simpleRedisLock.unLock();
//            lock.unlock();//Redisson 释放锁
//        }
//    }
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        //2.3实现一人一单功能（秒杀卷业务）
//        //2.3.1获取用户id
//        Long userId = UserHolder.getUser().getId();
//        //2.3.2 判断用户是否已经购买过
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();//统计数量
//        if (count > 0) {
//            //说明用户已经购买过
//            return Result.fail("您已经购买过一次,不能继续购买");
//        }
//        //3.3库存充足扣除库存，更新数据库
//        boolean success = seckillVoucherService.update()
//                .setSql("stock=stock-1")
//                .eq("voucher_id", voucherId) //where id = ? and stock >0
//                .gt("stock", 0).update(); //gt:greater than
//        //3.4不充足报错
//        if (!success) {
//            return Result.fail("库存不足");
//        }
//        //4.创建订单，返回订单id
//        VoucherOrder voucherOrder1=new VoucherOrder();
//        //6.1订单id（用全局唯一生成器）
//        long orderid = redisIdWorker.nextId("order");//创建id生成器对象,并写入到redis当中（看工具类的代码）
//        voucherOrder1.setId(orderid);
//        //6.2用户id
//        Long userId1 = UserHolder.getUser().getId();
//        voucherOrder1.setUserId(userId);
//        //6.3代金卷id
//        voucherOrder1.setVoucherId(voucherId);
//        //7保存订单到数据库
//        save(voucherOrder1);
//        return Result.ok(orderid);//返回订单id
//
//    }
