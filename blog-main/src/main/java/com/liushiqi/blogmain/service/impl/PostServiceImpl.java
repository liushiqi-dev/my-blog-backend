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
     *
     * @return
     */
    @Override
    public PostVo publishPost(Posts posts) {
        // TODO: 用户可输入摘要，用户如不输入摘要则使用正文前100个字符作为摘要
        // 生成摘要
        posts.setSummary(posts.getContent().substring(0, Math.min(99, posts.getContent().length())));

        // 设置作者ID
        Long authorId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        posts.setAuthorId(authorId);

        // 设置删除状态为0
        posts.setIsDeleted(0);
        // 设置文章状态为PUBLISHED
        posts.setStatus("PUBLISHED");

        // 保存文章
        postMapper.insert(posts);
        return postMapper.selectAll(posts.getId());
    }

    /**
     * 修改文章
     */
    @Override
    public PostVo updatePost(Posts posts) {
        // 更新文章
        postMapper.update(posts);

        // 查询文章信息
        return postMapper.selectAll(posts.getId());
    }

    /**
     * 获取文章详情
     */
    @Override
    public PostVo getPostDetail(Long id) {
        // 查询文章信息
        return postMapper.selectAll(id);
    }

}