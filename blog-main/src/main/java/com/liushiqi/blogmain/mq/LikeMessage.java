package com.liushiqi.blogmain.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 点赞消息体：消费者据此决定执行点赞还是取消的SQL
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeMessage implements Serializable {

    /** 文章ID */
    private Long postId;

    /** 用户ID */
    private Long userId;

    /** 是否点赞：1点赞 0取消点赞 */
    private Integer liked;
}
