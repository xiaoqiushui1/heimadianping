package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    //登录注册拦截器
    @Resource
    private StringRedisTemplate stringRedisTemplate;//添加redis操作对象
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器
        registry.addInterceptor(new LoginInterceptor())//创建拦截器对象，并且传入参数，spring底层自动调用类中的重写方法
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"//不需要拦截的路径
                ).order(1);//确保后执行
//刷新token拦截器
registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
        .addPathPatterns("/**").order(0);//拦截所用请求，且order默认为0，优先级最高先执行。

    }
}
