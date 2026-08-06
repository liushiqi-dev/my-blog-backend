package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.result.Result;
import com.liushiqi.blogmain.dto.request.PostRequest;
import com.liushiqi.blogmain.service.PostService;
import com.liushiqi.blogmain.vo.PostVo;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/posts")
public class PostController {

    @Autowired
    private PostService postService;

    /*
      发布文章（仅管理员）
      权限校验说明：
      1. @PreAuthorize("hasRole('ADMIN')") 在方法执行前检查用户是否具有ADMIN角色
      2. Spring Security会自动添加ROLE_前缀，因此数据库中存储的角色应为ADMIN而非ROLE_ADMIN
      3. 无权限时返回403 Forbidden，由Spring Security自动处理
      4. 相比在Service层手动检查，@PreAuthorize更符合声明式编程理念，代码更简洁
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result publishPost(@Valid @RequestBody PostRequest req) {
        PostVo postVo = postService.publishPost(req);
        log.info("文章发布成功");
        return Result.success(postVo);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result updatePost(@PathVariable Long id, @Valid @RequestBody PostRequest req) {
        req.setId(id);
        PostVo postVo = postService.updatePost(req);
        log.info("文章更新成功");
        return Result.success(postVo);
    }

    @GetMapping("/{id}")
    public Result getPostDetail(@PathVariable Long id) {
        PostVo postVo = postService.getPostDetail(id);
        return Result.success(postVo);
    }
}