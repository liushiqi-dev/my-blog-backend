package com.liushiqi.blogmain.service.impl;

import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.common.result.PageResult;
import com.liushiqi.blogmain.common.util.LikeRedisScript;
import com.liushiqi.blogmain.common.util.RedisUtils;
import com.liushiqi.blogmain.config.RabbitMQConfig;
import com.liushiqi.blogmain.dto.request.PostRequest;
import com.liushiqi.blogmain.mapper.PostMapper;
import com.liushiqi.blogmain.mq.LikeMessage;
import com.liushiqi.blogmain.service.PostService;
import com.liushiqi.blogmain.vo.PostVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 文章业务逻辑实现
 */
@Slf4j
@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private LikeRedisScript likeRedisScript;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVo createPost(PostRequest req) {
        if (req.getSummary()==null){
            req.setSummary(req.getContent().substring(0, Math.min(100, req.getContent().length())));
        }
        Long authorId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();

        Integer categoryCount= postMapper.countCategoriesByIds(req.getCategoryIds());
        if(categoryCount!=req.getCategoryIds().size()){
            throw new BusinessException("分类不存在");
        }
        postMapper.insert(authorId,req);
        postMapper.insertCategories(req.getId(),req.getCategoryIds());
//        redisTemplate.delete(redisTemplate.keys("posts:*"));//O(N)查找所有Key会阻塞Redis
        redisUtils.deleteByPattern("posts:*");//采用分批扫描，避免阻塞Redis
        return postMapper.findById(req.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVo updatePost(PostRequest req) {
        Integer categoryCount= postMapper.countCategoriesByIds(req.getCategoryIds());
        if(categoryCount!=req.getCategoryIds().size()){
            throw new BusinessException("分类不存在");
        }
        postMapper.update(req);
        postMapper.deleteCategoriesByPostId(req.getId());
        postMapper.insertCategories(req.getId(),req.getCategoryIds());
        redisUtils.deleteByPattern("posts:*");
        return postMapper.findById(req.getId());
    }

    @Override
    public PostVo getPostDetail(Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_ADMIN"));

        PostVo postVo = postMapper.findById(id);
        if(postVo==null||(!isAdmin&&!postVo.getStatus().equals("PUBLISHED"))){
            throw new BusinessException("文章不存在");
        }
        return postVo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        int rows = postMapper.deleteById(id);
        if(rows==0){
            throw new BusinessException("文章不存在");
        }
        postMapper.deleteCategoriesByPostId(id);
        redisUtils.deleteByPattern("posts:*");
    }

    @Override
    public PageResult listPosts(Integer page, Integer size, String status){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_ADMIN"));
        int offset=(page-1)*size;
        if(size>50){
            size=50;
        }
        if(!isAdmin){
            status="PUBLISHED";
        }
        String key="posts:"+status+":"+page+":"+size;
        Object cache = redisTemplate.opsForValue().get(key);
        if(cache!=null){
            return (PageResult) cache;
        }
        PageResult result = new PageResult<>(
                postMapper.findPage(offset,size,status),
                postMapper.getTotal(status),page,size);
        redisUtils.setWithRandomTtl(key, result, 30, 5, TimeUnit.MINUTES);
        return result;
    }

    @Override
    public void toggleLike(Long id) {
        Long userId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        if (userId == null) {
            throw new BusinessException("");
        }
        // Redis先行，Lua原子切换点赞状态（返回1点赞 0取消）
        Long liked = likeRedisScript.toggleLike(id, userId);
        // 发送消息至MQ，由消费者异步写入数据库（削峰+解耦）
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.LIKE_EXCHANGE,
                RabbitMQConfig.LIKE_ROUTING_KEY,
                new LikeMessage(id, userId, liked.intValue()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncLikeToDb(LikeMessage message) {
        if (message.getLiked() == 1) {
            // insert ignore：重复消息返回0，此时不累加计数，保证幂等
            int rows = postMapper.insertLike(message.getPostId(), message.getUserId());
            if (rows > 0) {
                postMapper.updateLikeCount(message.getPostId(), 1);
            }
        } else {
            int rows = postMapper.deleteLike(message.getPostId(), message.getUserId());
            if (rows > 0) {
                postMapper.updateLikeCount(message.getPostId(), -1);
            }
        }
    }

}