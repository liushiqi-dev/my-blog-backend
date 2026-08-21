package com.liushiqi.blogmain.service;

import com.liushiqi.blogmain.common.result.PageResult;
import com.liushiqi.blogmain.dto.request.PostRequest;
import com.liushiqi.blogmain.mq.LikeMessage;
import com.liushiqi.blogmain.vo.PostVo;


/**
 * 文章业务逻辑接口
 */
public interface PostService {
    PostVo createPost(PostRequest req);

    PostVo updatePost(PostRequest req);

    PostVo getPostDetail(Long id);

    void deletePost(Long id);

    PageResult listPosts(Integer page, Integer size, String status);

    void toggleLike(Long id);

    /**
     * 将Redis中的点赞状态同步至数据库（由MQ消费者调用）
     */
    void syncLikeToDb(LikeMessage message);
}