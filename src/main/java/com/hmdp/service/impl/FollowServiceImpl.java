package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
   private StringRedisTemplate  stringRedisTemplate;
    @Resource
   private IUserService userService;
    @Override
    public Result isFollow(Long followUserId) {
//1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();//只用计数就行
        //3.判断是否关注
        return Result.ok(count > 0);
    }
//关注和取关以及共同关注的实现
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //Redis key
        String Key ="follows" + userId;
        //1.判断到底是关注还是取关
        if (isFollow) {
            //2.关注，新增数据
            Follow follow = new Follow();
            follow.setFollowUserId( followUserId);
            follow.setUserId(userId);//保存博文的用户id,也就是当前登录用户的id
      boolean isSuccess=this.save(follow);//保存数据库
            if (isSuccess){
                //把关注用户的id，放入redis的set集合当中 sadd userId followUserId_userId=userId(这次要查寻当前登录用户的id有谁关注，也就是当前博文作者的粉丝有谁)
        stringRedisTemplate.opsForSet().add(Key,followUserId.toString());
            }
        } else {
            //3.取关，删除,delect * from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));//mybatisplus写法,也可以用mybatis
           if (isSuccess) {
               stringRedisTemplate.opsForSet().remove(Key, followUserId.toString());
           }
        }
        return Result.ok();
    }
    @Override
    public Result commonfollows(Long id) {
        //1.查询当前用户
        Long UserId=UserHolder.getUser().getId();
        //2.获取俩个reids缓存里set的key
        String Key1="follows" + UserId;//当前用户
        String Key2="follows" +id;
        Set<String> commonId = stringRedisTemplate.opsForSet().intersect(Key1, Key2);
        if (commonId ==null && commonId.isEmpty() ){
            //无交集
            return Result.ok(Collections.emptyList());
        }
         //3.解析id集合
        List<Long> ids  = commonId.stream().map(Long::valueOf).collect(Collectors.toList());
        //List<Long> ids = new ArrayList<>();
        //for (String strId : commonId) {
        //    Long numId = Long.valueOf(strId);
        //    ids.add(numId);
        //}相当于这段代码
        //4.根据id查询用户,进行map映射
        List<UserDTO> commonlistids = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        //1.批量查用户
        //List<User> userList = userService.listByIds(ids);
        //2.新建集合存DTO
        //List<UserDTO> commonlistds = new ArrayList<>();
        // 3.循环每个User，转DTO
        //for (User user : userList) {
        //    UserDTO dto = new UserDTO();
        //    BeanUtil.copyProperties(user, dto);
        //    commonlistds.add(dto);
        //}
        return Result.ok(commonlistids);
    }
}

