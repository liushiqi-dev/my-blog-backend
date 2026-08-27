package com.liushiqi.blogmain.task;

import com.liushiqi.blogmain.mapper.PostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 浏览量定时落库任务
 * <p>
 * 浏览量写入链路：详情接口只对 Redis 执行 INCR（原子自增，不碰 MySQL），
 * 本任务每隔固定时间把 Redis 中累计的增量批量刷回 MySQL。
 * <p>
 * 为什么浏览量用定时任务、点赞用 MQ？
 * - 点赞需要"先读状态再决定加/减"，且要即时反馈，适合 MQ 逐条异步落库
 * - 浏览量是"无脑 +1"的纯累加，不依赖状态判断，攒一批再写库更省数据库压力
 * <p>
 * 💡 八股文关联：
 * - fixedDelay vs fixedRate：fixedDelay 是上次执行【结束后】再计时，任务慢时不会重叠执行
 * - Redis 的 GETDEL（这里用 getAndDelete）是原子命令，避免"先 GET 再 DEL"两步之间又有新浏览导致的丢数
 * - 多实例部署时 @Scheduled 每个节点都会跑，需要分布式锁保证只有一个节点执行（当前单机部署不涉及）
 */
@Slf4j
@Component
public class ViewCountSyncTask {

    private static final String VIEW_KEY_PREFIX = "post:views:";

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 每5分钟同步一次：上次执行完毕后间隔5分钟再跑
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void syncViewCountToDb() {
        // scan 渐进式扫描所有浏览量 key（避免 KEYS * 阻塞 Redis）
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(VIEW_KEY_PREFIX + "*").count(100).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                // GETDEL 原子取出并删除：取走的是"自上次同步以来的增量"，取完 key 清零重新累计
                Object delta = redisTemplate.opsForValue().getAndDelete(key);
                if (delta == null) {
                    continue;
                }
                Long postId = Long.valueOf(key.substring(VIEW_KEY_PREFIX.length()));
                postMapper.updateViewCount(postId, ((Number) delta).intValue());
            }
        }
        log.info("浏览量同步完成");
    }
}
