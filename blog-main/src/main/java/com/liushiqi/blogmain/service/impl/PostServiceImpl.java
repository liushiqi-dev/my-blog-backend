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
import org.springframework.dao.DataAccessException;
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
        // 未登录/匿名用户不碰 Redis 也不查库，默认未点赞；userId 同时供降级分支复用
        Object principal = auth == null ? null : auth.getPrincipal();
        Long userId = (principal instanceof Number) ? ((Number) principal).longValue() : null;

        // 三处 Redis 调用统一降级：key 不存在走各自原有兜底；Redis 连不上/超时抛异常时
        // 拦截后沿用 findById 查出的 DB 旧值返回，避免整个详情接口 500
        try {
            // 浏览量+1：INCR 原子自增，只写 Redis 不碰 MySQL（高频写转移给 Redis）
            // 落库交给 ViewCountSyncTask 定时任务批量完成
            Long pendingViews = redisTemplate.opsForValue().increment("post:views:" + id);
            // MySQL 里是上次同步后的旧值，补上 Redis 中尚未同步的增量，返回实时浏览量
            postVo.setViewCount(postVo.getViewCount() + pendingViews.intValue());

            // 点赞数：优先取 Redis 当前值（Lua 每按一次对 post:likeCount:{id} 做+1/-1，是瞬时量），
            // 无 key（尚未点过）才用 DB 旧值；MQ 异步落库后 DB 会追上来，最终一致
            Integer likeCount = (Integer) redisTemplate.opsForValue().get("post:likeCount:" + id);
            postVo.setLikeCount(likeCount != null ? likeCount : postVo.getLikeCount());

            // 当前用户是否已点赞：查 Redis 点赞集合
            postVo.setLiked(userId != null &&
                    Boolean.TRUE.equals(redisTemplate.opsForSet().isMember("post:likes:" + id, userId.toString())));
        } catch (DataAccessException e) {
            // Redis 故障降级：浏览/点赞数不重查，postVo 里已是 findById 的 DB 旧值；
            // 已点赞状态改查 post_likes 表兜底（MQ 异步落库最终一致，降级瞬间刚点的赞可能短暂查不到）
            log.warn("Redis不可用，浏览/点赞数回显DB旧值，已点赞状态查库兜底, postId={}", id, e);
            postVo.setLiked(userId != null && postMapper.countLike(id, userId) > 0);
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