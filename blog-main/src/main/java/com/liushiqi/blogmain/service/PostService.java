package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.entity.Posts;
import com.liushiqi.blogmain.vo.PostVo;

/**
 * 文章业务逻辑接口
 */
public interface PostService {
    /**
     * 发布文章
     *
     * @return
     */
    PostVo publishPost(Posts posts);

    //修改文章
    PostVo updatePost(Posts posts);

    PostVo getPostDetail(Long id);
}