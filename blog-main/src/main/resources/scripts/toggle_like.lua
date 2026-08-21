-- 点赞/取消点赞切换脚本
-- KEYS[1] = post:likes:{postId}    (Set: 存储已点赞用户ID)
-- KEYS[2] = post:likeCount:{postId} (String: 点赞计数)
-- ARGV[1] = userId

if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then
    redis.call('SREM', KEYS[1], ARGV[1])
    redis.call('DECR', KEYS[2])
    return 0
else
    redis.call('SADD', KEYS[1], ARGV[1])
    redis.call('INCR', KEYS[2])
    return 1
end