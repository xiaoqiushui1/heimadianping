package com.hmdp.utils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
public class LoginInterceptor implements HandlerInterceptor {
@Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
      //1.判断是否放行（ThreadLocal是否有用户）
   if(UserHolder.getUser()==null){//看mvc的配置中排除了那些必须要拦截的请求
       //没有登录，拦截请 求
       response.setStatus(401);
       //拦截
       return false;
   }
   //2.放行
              return true;
    }

}