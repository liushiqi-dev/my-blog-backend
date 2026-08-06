package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.entity.Posts;
import com.liushiqi.blogmain.vo.PostVo;

/**
 * 文章业务逻辑接口
 */
public interface PostService {
    PostVo publishPost(Posts posts);

    PostVo updatePost(Posts posts);

    PostVo getPostDetail(Long id);
}