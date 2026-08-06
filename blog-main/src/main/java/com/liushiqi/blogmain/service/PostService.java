package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.dto.request.PostRequest;
import com.liushiqi.blogmain.vo.PostVo;

/**
 * 文章业务逻辑接口
 */
public interface PostService {
    PostVo publishPost(PostRequest req);

    PostVo updatePost(PostRequest req);

    PostVo getPostDetail(Long id);

    PostVo getPostDetailAll(Long id);
}