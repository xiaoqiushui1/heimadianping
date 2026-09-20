package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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
    private IUserService userService;
    @Override
    public Result querHotBlog(Integer current) {     // 根据id查询多个博文
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));//创建一个分页对象
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this :: extracted);//this指的是records
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
        return Result.ok(blog);
    }
    private void extracted(Blog blog) {//ctrl+alt+m可以封装一个函数
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
