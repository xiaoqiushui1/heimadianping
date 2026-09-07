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
    @Transactional
    public Result seckillVoucher(Long voucherId) {

        //优惠卷秒杀下单
        //1.根据前端传递id查询数据库是否有此秒杀卷
        SeckillVoucher voucherOrder=seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
   if (voucherOrder.getBeginTime().isAfter(LocalDateTime.now())){
       //2.1尚未开始
       return Result.fail("尚未开始！");
        }
   //2.1.2判断秒杀是否结束
        if(voucherOrder.getEndTime().isBefore(LocalDateTime.now()))
        {
            //秒杀已经结束
            return Result.fail("秒杀已经结束");
        }
        //2.2判断库存是否充足
        if (voucherOrder.getStock()<1){
            //库存不足
            return Result.fail("库存不足");
        }
        //2.3库存充足扣除库存，更新数据库
       Boolean  sw1= seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id",voucherId).update();
        //2.4不充足报错
        if(!sw1){
            return Result.fail("库存不足");
        }
        //3.创建订单，返回订单id
        VoucherOrder voucherOrder1=new VoucherOrder();
        //6.1订单id（用全局唯一生成器）
        long orderid= redisIdWorker.nextId("order");
        voucherOrder1.setId(orderid);
        //6.2用户id
        Long userId=UserHolder.getUser().getId();
        voucherOrder1.setUserId(userId);
        //6.3代金卷id
        voucherOrder1.setVoucherId(voucherId);
        //7保存订单到数据库
        save(voucherOrder1);



        return Result.ok(orderid);
    }
}
