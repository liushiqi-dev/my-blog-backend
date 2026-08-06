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

    @Override
    public PostVo publishPost(Posts posts) {
        // TODO: 用户可输入摘要，用户如不输入摘要则使用正文前100个字符作为摘要
        posts.setSummary(posts.getContent().substring(0, Math.min(99, posts.getContent().length())));

        Long authorId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        posts.setAuthorId(authorId);

        posts.setIsDeleted(0);
        posts.setStatus("PUBLISHED");

        postMapper.insert(posts);
        return postMapper.selectAll(posts.getId());
    }

    @Override
    public PostVo updatePost(Posts posts) {
        postMapper.update(posts);
        // Fixme: 如将文章状态更新为草稿，则无法查询到文章详情。因为该SQL语句只查询已发布的文章。
        return postMapper.selectAll(posts.getId());
    }

    @Override
    public PostVo getPostDetail(Long id) {
        return postMapper.selectAll(id);
    }

}