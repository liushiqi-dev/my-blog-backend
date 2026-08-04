package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.result.Result;
import com.liushiqi.blogmain.entity.Posts;
import com.liushiqi.blogmain.service.PostService;
import com.liushiqi.blogmain.vo.PostVo;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章控制器
 */
@Slf4j
@RestController
@RequestMapping("/posts")
@PreAuthorize("hasRole('ADMIN')")
public class PostController {

    @Autowired
    private PostService postService;

    /**
     * 发布文章（仅管理员）
     * 权限校验说明：
     * 1. @PreAuthorize("hasRole('ADMIN')") 在方法执行前检查用户是否具有ADMIN角色
     * 2. Spring Security会自动添加ROLE_前缀，因此数据库中存储的角色应为ADMIN而非ROLE_ADMIN
     * 3. 无权限时返回403 Forbidden，由Spring Security自动处理
     * 4. 相比在Service层手动检查，@PreAuthorize更符合声明式编程理念，代码更简洁
     */
    @PostMapping
    public Result publishPost(@Valid @RequestBody Posts posts) {
        postService.publishPost(posts);
        log.info("文章发布成功");
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result updatePost(@PathVariable Long id, @Valid @RequestBody Posts posts) {
        posts.setId(id);
        PostVo postVo = postService.updatePost(posts);
        log.info("文章更新成功");
        return Result.success(postVo);
    }
}