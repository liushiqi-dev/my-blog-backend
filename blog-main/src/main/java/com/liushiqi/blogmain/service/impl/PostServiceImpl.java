package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.dto.request.PostRequest;
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
    public PostVo publishPost(PostRequest req) {
        // TODO: 用户可输入摘要，用户如不输入摘要则使用正文前100个字符作为摘要
        Posts posts = new Posts();
        posts.setTitle(req.getTitle());
        posts.setContent(req.getContent());
        posts.setSummary(req.getContent().substring(0, Math.min(99, req.getContent().length())));

        Long authorId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        posts.setAuthorId(authorId);

        posts.setIsDeleted(0);
        posts.setStatus("PUBLISHED");

        postMapper.insert(posts);
        return postMapper.findById(posts.getId());
    }

    @Override
    public PostVo updatePost(PostRequest req) {
        Posts posts = new Posts();
        posts.setId(req.getId());
        posts.setTitle(req.getTitle());
        posts.setContent(req.getContent());
        posts.setSummary(req.getSummary());
        postMapper.update(posts);
        return postMapper.findById(posts.getId());
    }

    @Override
    public PostVo getPostDetail(Long id) {
        PostVo postVo = postMapper.findById(id);
        if(postVo==null||!postVo.getStatus().equals("PUBLISHED")){
            throw new BusinessException("文章不存在");
        }
        return postVo;
    }

    @Override
    public PostVo getPostDetailAll(Long id) {
        PostVo postVo = postMapper.findById(id);
        if(postVo==null){
            throw new BusinessException("文章不存在");
        }
        return postVo;
    }

    @Override
    public void deletePost(Long id) {
        int rows = postMapper.deleteById(id);
        if(rows==0){
            throw new BusinessException("文章不存在");
        }
    }

}