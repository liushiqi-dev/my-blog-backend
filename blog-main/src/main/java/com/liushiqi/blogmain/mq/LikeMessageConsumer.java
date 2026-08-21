package com.liushiqi.blogmain.mq;

import com.liushiqi.blogmain.config.RabbitMQConfig;
import com.liushiqi.blogmain.service.PostService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 点赞消息消费者：将Redis中的点赞状态异步同步至MySQL
 * 手动ACK：处理成功才确认消息，失败重新入队等待重试
 */
@Slf4j
@Component
public class LikeMessageConsumer {

    @Autowired
    private PostService postService;

    @RabbitListener(queues = RabbitMQConfig.LIKE_QUEUE)
    public void handleLikeMessage(LikeMessage message, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            // 事务方法：返回即提交，之后才ACK，保证落库成功才确认消息
            postService.syncLikeToDb(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 失败不确认，消息重新入队，恢复后重试（SQL幂等保证不重复计数）
            log.error("点赞消息处理失败，重新入队: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
