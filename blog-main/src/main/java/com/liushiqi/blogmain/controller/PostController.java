package com.liushiqi.blogmain.controller;

import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.common.result.Result;
import com.liushiqi.blogmain.common.util.SlidingWindowRateLimiter;
import com.liushiqi.blogmain.dto.request.PostRequest;
import com.liushiqi.blogmain.service.PostService;
import com.liushiqi.blogmain.vo.PostVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/posts")
public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private SlidingWindowRateLimiter slidingWindowRateLimiter;

    /**
     * 点赞风控阈值：同一用户 60 秒内最多 10 次。
     * 防的不是数据错（点赞本身幂等），防的是脚本：每次 toggle 都产生一条 MQ 消息，
     * 高频刷会造成队列积压、落库延迟、消费端 DB 写放大。输入侧限流 = 削峰的第一道闸。
     */
    private static final int LIKE_LIMIT_THRESHOLD = 10;
    private static final int LIKE_LIMIT_WINDOW_SECONDS = 60;

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

    @PatchMapping("/{id}/like")
    public Result toggleLike(@PathVariable Long id) {
        // 风控：按用户维度做滑动窗口限流，防脚本高频刷赞。
        // 选 userId 而非 IP：IP 维度会让公司/校园网 NAT 出口的正常用户被连坐误伤；
        // 选全局桶而非"用户+文章"桶：脚本遍历文章 ID 即可绕过分桶，全局桶才拦得住。
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        if (!(principal instanceof Number)) {
            throw new BusinessException("请先登录");
        }
        if (!slidingWindowRateLimiter.tryAcquire("rate:like:" + ((Number) principal).longValue(),
                LIKE_LIMIT_THRESHOLD, LIKE_LIMIT_WINDOW_SECONDS)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
        postService.toggleLike(id);
        return Result.success();
    }
}