package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.entity.Posts;
import com.liushiqi.blogmain.mapper.PostMapper;
import com.liushiqi.blogmain.service.PostService;
import com.liushiqi.blogmain.vo.PostVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 文章业务逻辑实现
 */
@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostMapper postMapper;

    /**
     * 发布文章
     */
    @Override
    public void publishPost(Posts posts) {
        // 生成摘要
        posts.setSummary(posts.getContent().substring(0, Math.min(99, posts.getContent().length())));

        // 设置作者ID
        Long authorId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        posts.setAuthorId(authorId);

        // 设置删除状态为0
        posts.setIsDeleted(0);
        // 保存文章
        postMapper.insert(posts);
    }

    /**
     * 修改文章
     */
    @Override
    public PostVo updatePost(Posts posts) {
        // 更新文章
        postMapper.update(posts);

        // 查询文章信息
        PostVo postVo=postMapper.selectAll();

        //从SecurityContext中获取当前登录用户名
        String authorName = (String) Objects.requireNonNull
                (SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        postVo.setAuthorName(authorName);
        return postVo;
    }
}