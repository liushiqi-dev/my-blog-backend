package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.result.Result;
import com.liushiqi.blogmain.dto.request.PostRequest;
import com.liushiqi.blogmain.service.PostService;
import com.liushiqi.blogmain.vo.PostVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/posts")
public class PostController {

    @Autowired
    private PostService postService;

    /*
      创建文章（仅管理员）
      权限校验说明：
      1. @PreAuthorize("hasRole('ADMIN')") 在方法执行前检查用户是否具有ADMIN角色
      2. Spring Security会自动添加ROLE_前缀，因此数据库中存储的角色应为ADMIN而非ROLE_ADMIN
      3. 无权限时返回403 Forbidden，由Spring Security自动处理
      4. 相比在Service层手动检查，@PreAuthorize更符合声明式编程理念，代码更简洁
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result createPost(@Valid @RequestBody PostRequest req) {
        PostVo postVo = postService.createPost(req);
        log.info("创建文章成功，文章ID：{}，状态：{}", postVo.getId(), postVo.getStatus());
        return Result.success(postVo);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result updatePost(@PathVariable Long id, @Valid @RequestBody PostRequest req) {
        req.setId(id);
        PostVo postVo = postService.updatePost(req);
        log.info("文章更新成功，文章ID：{}", postVo.getId());
        return Result.success(postVo);
    }

    @GetMapping("/{id}")
    public Result getPostDetail(@PathVariable Long id) {
        PostVo postVo = postService.getPostDetail(id);
        log.info("获取文章详情成功，文章ID：{}", postVo.getId());
        return Result.success(postVo);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result deletePost(@PathVariable Long id) {
        postService.deletePost(id);
        log.info("删除文章成功，文章ID：{}", id);
        return Result.success();
    }

    @Validated
    @GetMapping
    public Result listPosts(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(10) Integer size,
            @RequestParam(required = false) String status
            ) {
        log.info("获取文章列表，分页参数：page={}, size={}, status={}", page, size, status);
        return Result.success(postService.listPosts(page,size,status));
    }
}