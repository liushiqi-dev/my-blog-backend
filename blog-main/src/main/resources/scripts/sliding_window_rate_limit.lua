-- 滑动窗口限流脚本
-- KEYS[1] = 限流键（ZSet：member=请求唯一ID，score=时间戳ms）
-- ARGV[1] = 请求唯一ID
-- ARGV[2] = 当前时间戳（ms）
-- ARGV[3] = 窗口长度（ms）
-- ARGV[4] = 窗口内最大放行次数

local key = KEYS[1]
local memberId = ARGV[1]
local now = tonumber(ARGV[2])
local window = tonumber(ARGV[3])
local threshold = tonumber(ARGV[4])

-- 移除窗口外的过期记录
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

-- 统计当前窗口（相当于所有）内的请求数
local count = redis.call('ZCARD', key)

if count < threshold then
    -- 放行：记录本次请求
    redis.call('ZADD', key, now, memberId)
    redis.call('PEXPIRE', key, window)
    return true
end

-- 拒绝：不记录，返回false(Redis 执行 Lua 脚本时，return false 和 nil 的结果完全一样——都转成 nil 回复)
return false
