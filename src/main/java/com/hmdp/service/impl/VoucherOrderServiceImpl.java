package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //优惠卷秒杀下单
        //1.根据前端传递id查询数据库是否有此秒杀卷
        SeckillVoucher voucherOrder = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucherOrder.getBeginTime().isAfter(LocalDateTime.now())) {
            //2.1尚未开始
            return Result.fail("尚未开始！");
        }
        //2.1.2判断秒杀是否结束
        if (voucherOrder.getEndTime().isBefore(LocalDateTime.now())) {
            //秒杀已经结束
            return Result.fail("秒杀已经结束");
        }
        //2.2判断库存是否充足
        if (voucherOrder.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //给每个客户分配唯一锁,实现一人一单
        synchronized (userId.toString().intern()) {//锁对象控制的代码块,黑马一人一单的讲解底层
            IVoucherOrderService  proxy = (IVoucherOrderService) AopContext.currentProxy();//获取当前代理对象，也就是接口类IVoucherOrderService的代理对象(接口类的作用之一)
            return proxy.createVoucherOrder(voucherId);//createVoucherOrder(voucherId)默认是当前对象调用(this(也就是voucherOrderserviceImpl)),而当前对象调用会造成事务失效。
        }//给每个用户分配唯一锁

    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //2.3实现一人一单功能（秒杀卷业务）
        //2.3.1获取用户id
        Long userId = UserHolder.getUser().getId();
        //2.3.2 判断用户是否已经购买过
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();//统计数量
        if (count > 0) {
            //说明用户已经购买过
            return Result.fail("您已经购买过一次,不能继续购买");
        }
        //3.3库存充足扣除库存，更新数据库
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId) //where id = ? and stock >0
                .gt("stock", 0).update(); //gt:greater than
        //3.4不充足报错
        if (!success) {
            return Result.fail("库存不足");
        }
        //4.创建订单，返回订单id
        VoucherOrder voucherOrder1 = new VoucherOrder();
        //6.1订单id（用全局唯一生成器）
        long orderid = redisIdWorker.nextId("order");
        voucherOrder1.setId(orderid);
        //6.2用户id
        voucherOrder1.setUserId(UserHolder.getUser().getId());
        //6.3代金卷id
        voucherOrder1.setVoucherId(voucherId);
        //7保存订单到数据库
        save(voucherOrder1);
        return Result.ok(orderid);
    }
}