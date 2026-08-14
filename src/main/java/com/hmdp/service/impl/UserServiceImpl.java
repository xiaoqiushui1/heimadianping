package com.hmdp.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.SystemConstants;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Service;


import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
@Resource
private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        //2.不符合报错
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！");
        }
        //3.生成验证码
       String code= RandomUtil.randomNumbers(6);
        //4.保存验证码到redis//set key value ex 120s（string类型）
      stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+ phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);
        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        1.校验手机号
        String phone=loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone))
        {
            return Result.fail("手机号格式错误！");
        }
//        2.校验验证码，从redis获取验证码//get Login:code： phone
//        Object cachcode=session.getAttribute("code");
        String cachcode=stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+ phone);
        String code=loginForm.getCode();
//        3.不一致报错
        if (cachcode == null || !cachcode.equals(code)){
            return Result.fail("验证码错误");
        }
//        4.一致，根据手机号查询用户 select * from tb_user where phone=?
       User user=query().eq("phone" ,phone).one();//select * from tb_user where phone=?
//        5.判断用户是否存在
        if (user==null) {
//        6.不存在，创建用户并保存
        user =creatUserWithphone(phone);
        }
//        7.保存用户信息到redis
        //7.1随机生成token，作为登录令牌
       String token= UUID.randomUUID().toString( true);//不生成横划线的token（32位字符），生成36位字符
        //7.2将user对象转为Hash存储
         UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);//将user对象转为UserDTO对象
//        Map<String, Object> userMap =BeanUtil.beanToMap(userDTO);//reids 中token中存储的key-value（map），应为userDto有long 类型，
//   而Stringredistemplate这个对象底层就指定了Map<String，String>，不能转为map，所以自己new一个map或者
       // 法一：
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
//       //法二：易懂
//        Map<String,String> userMap=new HashMap<>();//自己new一个map，字段属性对应key，字段值对应value，为后续存储redis做准备。好用！
//        userMap.put("id",userDTO.getId().toString());
//        userMap.put("nickName",userDTO.getNickName());
//        userMap.put("icon",userDTO.getIcon());
        //7.3存储
        String tokenKey=LOGIN_USER_KEY+ token;
stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+ token,userMap);//存储一个key对应全部的map
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        //7.4设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);//登录有效期36000 分钟
        //8.返回token
        return Result.ok(token);
    }
    public User creatUserWithphone(String phone){//创建用户，使用MYBATISPLUS快速创建到数据库表中
        User user=new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
         save(user);
        return user;
    }
}
