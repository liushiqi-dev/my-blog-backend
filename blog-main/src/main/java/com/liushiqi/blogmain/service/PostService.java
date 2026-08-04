package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.entity.Posts;

/**
 * 文章业务逻辑接口
 */
public interface PostService {
    /**
     * 发布文章
     */
    void publishPost(Posts posts);
}