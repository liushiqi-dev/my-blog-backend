package com.liushiqi.blogmain.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ========== 点赞 ==========
    public static final String LIKE_EXCHANGE = "blog.like.exchange";
    public static final String LIKE_QUEUE = "blog.like.queue";
    public static final String LIKE_ROUTING_KEY = "like.post";

    @Bean
    public DirectExchange likeExchange() {
        return new DirectExchange(LIKE_EXCHANGE, true, false);
    }

    @Bean
    public Queue likeQueue() {
        return QueueBuilder.durable(LIKE_QUEUE).build();
    }

    @Bean
    public Binding likeBinding() {
        return BindingBuilder.bind(likeQueue()).to(likeExchange()).with(LIKE_ROUTING_KEY);
    }

    /**
     * 消息体采用JSON序列化，生产者与消费者可直接收发对象
     */
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}