package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result likeBlog(Long id) {
        //1.获得登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞
       String key= BLOG_LIKED_KEY+id;
       Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if(BooleanUtil.isFalse(isMember)) {
        //3.如果未点赞，可以点赞
            //3.1.数据库点赞数+1
       boolean isSuccess =update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2.保存用户到redis的set集合
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(key, userId.toString());
            }
        }else {
            //4.如果已点赞，取消点赞
            //4.1. 数据库点赞数-1
            boolean isSuccess =update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2.从redis的set集合中移除当前用户
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove(key, userId.toString());//移除用户
            }
        }
        return Result.ok();
    }

    @Resource
    private IUserService userService;
    @Override
    public Result querHotBlog(Integer current) {     // 根据id查询多个博文
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));//创建一个分页对象
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
           this.extracted(blog);//获取用户并赋值给博文的属性
            this.isBlogLiked(blog);//判断是否点赞(这个步骤需要登录，要不获取不到userId)
        });//this指的是records
        return Result.ok(records);
    }
// // 获取当前页数据
//    List<Blog> records = page.getRecords();
//
//    // ========== 替换原来 records.forEach(this::extracted); ==========
//    for (Blog blog : records) {
//        extracted(blog);
//    }
    @Override
    public Result queryBlogById(Long id) {// 查询单个博文
        //1.查询blog
        Blog blog = getById(id);//查询数据库的blog
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        //2.查询blog有关的用户
        extracted(blog);
        //3.查询blog的点赞数量
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void  isBlogLiked  (Blog blog) {
        Long userId= UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞
        String key= BLOG_LIKED_KEY+blog.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());//必须要用tostring要不记录的是地址，找不到会报空
       blog.setIsLike(BooleanUtil.isTrue(isMember));

    }

    private void extracted(Blog blog) {//ctrl+alt+m可以封装一个函数
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
