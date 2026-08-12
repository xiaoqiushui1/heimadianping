package com.hmdp.utils;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;


import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

public class LoginInterceptor implements HandlerInterceptor {
  //自己new的类不能用@Autowired，添加到配置类就可以用构造器注入对象
private StringRedisTemplate stringRedisTemplate;
public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
}
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
      //1.获取请求头中的token
//        HttpSession session= request.getSession();
        String token=request.getHeader("authorization");
        //不存在
        if(StrUtil.isBlank(token)){
            //未登录，拦截
            response.setStatus(401);
            return false;
        }
       //2.基于token获取redis中的用户
//        Object user = session.getAttribute("user");
        String key = LOGIN_USER_KEY + token;
         Map<Object, Object>  userMap=stringRedisTemplate.opsForHash().entries(key);//根据一个key获取所对应所用map集合，get只能获取一个key对应的一个map集合 Hgetall key（获得对应key的全部map）
       //3.判断用户是否存在
        if(userMap.isEmpty()){
            //4.不存在，拦截
            response.setStatus(401);
            return false;
        }
        //5.将查询到的用户数据转为UserDTO对象
      UserDTO userDTO =  BeanUtil.fillBeanWithMap(userMap,new UserDTO(), false);
        //6.更新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);//登录有效期36000 分钟
       //7.存在，保存到ThreadLocal
        UserHolder.saveUser((UserDTO) userDTO);
       //8.放行
              return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
       //移除用户
        UserHolder.removeUser();
    }
}