-- KEYS[1]: 商品庫存 Key (例如: "seckill:stock:101")
-- KEYS[2]: 購買記錄 Key (用 Set 儲存已買過的 UserID，防止一人多買)
-- ARGV[1]: User ID

local stockKey = KEYS[1]
local userHistoryKey = KEYS[2]
local userId = ARGV[1]

-- 1. 檢查是否重複購買 (Idempotency)
if redis.call('SISMEMBER', userHistoryKey, userId) == 1 then
    return -1 -- 錯誤碼 -1: 代表重複購買
end

-- 2. 檢查庫存
local stock = tonumber(redis.call('GET', stockKey))
if stock == nil or stock <= 0 then
    return 0 -- 錯誤碼 0: 代表庫存不足 (秒殺失敗)
end

-- 3. 扣減庫存 & 記錄用戶 (原子操作)
redis.call('DECR', stockKey)
redis.call('SADD', userHistoryKey, userId)

return 1 -- 成功碼 1: 秒殺成功