package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    public Result queryBlogLikes(Long id) {
        //1.在redis中查找top5的点赞用户 zrange key 0，4
        String key= BLOG_LIKED_KEY+id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);//获取前5个
if (top5 == null || top5.isEmpty()){
    return Result.ok(Collections.emptyList());//返回个空集合
}
//2.解析其中的用户id,之后根据用户id在mysql中查询用户的信息将其封装到集合userdto属性中
   List<Long> ids= top5.stream().map(Long::valueOf).collect(Collectors.toList());//转为Long之后存入list集合
        //List <Long> ids=new ArrayList<>();
        //等价于for(String str :: top5){
          //Long userId=Long.valueOf(str);
        //ids.add(userId)
        // }
    //拼接0-4的id字符串，方便mysql查询
        String idstr= StrUtil.join(",", ids);
    //3.根据用户id查询用户WHERE ID In(5,1) ORDER BY FIELD(id,5,1),并将其封装为userdto对象之中
   List<UserDTO> userDTOS = userService.query()
           .in("id",ids).last("ORDER BY FIELD(id," + idstr + ")").list()
           .stream()
           .map(user -> BeanUtil.copyProperties(user,UserDTO.class))//将user转为userDTO
           .collect(Collectors.toList());
//4.返回

        return  Result.ok(userDTOS);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获得登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞
       String key= BLOG_LIKED_KEY+id;
//       Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        //使用sortset实现点赞排行榜
     Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if(score == null) {
        //3.如果未点赞，可以点赞
            //3.1.数据库点赞数+1
       boolean isSuccess =update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2.保存用户到redis的set集合,Zadd key value score
            if (isSuccess){
//                stringRedisTemplate.opsForSet().add(key, userId.toString());
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());//value记录的是当前时间戳
            }
        }else {
            //4.如果已点赞，取消点赞
            //4.1. 数据库点赞数-1
            boolean isSuccess =update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2.从redis的set集合中移除当前用户
            if (isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());//移除用户
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
        if (UserHolder.getUser()==null){
            //用户未登录，无需查看是否点赞，但是可以看其他点赞 数
            return;
        }
        Long userId= UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞
        String key= BLOG_LIKED_KEY+blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());//必须要用tostring要不记录的是地址，找不到会报空
        blog.setIsLike(score !=null);//这个!=是运算符，最后自动生成布尔值.

    }

    private void extracted(Blog blog) {//ctrl+alt+m可以封装一个函数
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
