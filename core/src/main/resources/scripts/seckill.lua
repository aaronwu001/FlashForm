-- KEYS[1]: form quota (e.g. "form:quota:101")
-- KEYS[2]: Submitted list (Set structure, e.g. "form:submitted:101")
-- ARGV[1]: User ID

local quotaKey = KEYS[1]
local submittedKey = KEYS[2]
local userId = ARGV[1]

-- 1. Check repeated submission (Idempotency)
if redis.call('SISMEMBER', submittedKey, userId) == 1 then
    return -1 -- error code -1: repeated submission
end

-- 2. Check remaining quota
local currentQuota = tonumber(redis.call('GET', quotaKey))

-- if quota does not exist (nil) or used up (<= 0)
if currentQuota == nil or currentQuota <= 0 then
    return 0 -- error code 0: quota full
end

-- 3. 執行提交 (原子操作)
-- decrement quota (Quota - 1)
redis.call('DECR', quotaKey)
-- add user to submitted list
redis.call('SADD', submittedKey, userId)

return 1 -- success code 1: submission successful