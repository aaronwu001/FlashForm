-- FlashForm Seckill Atomic Script
-- Purpose: Handle stock deduction and user recording atomically to prevent overselling.

-- KEYS[1]: form:quota:{id}      (The Redis key holding the remaining stock count)
-- KEYS[2]: form:submitted:{id}  (The Redis Set key holding user IDs who successfully submitted)
-- ARGV[1]: userId               (The ID of the user attempting to submit)

local quotaKey = KEYS[1]
local submittedKey = KEYS[2]
local userId = ARGV[1]

-- 1. Idempotency Check: Verify if the user has already submitted.
-- If the user ID exists in the set, return -1 to indicate duplicate submission.
if redis.call('sismember', submittedKey, userId) == 1 then
    return -1
end

-- 2. Stock Check: Retrieve the current quota.
local stock = tonumber(redis.call('get', quotaKey))

-- Edge Case Handling: If the quota key does not exist, Redis returns nil.
-- We treat a missing key as "Sold Out" (0) to prevent runtime errors.
if stock == nil then
    return 0
end

-- If stock is 0 or less, return 0 (Sold Out).
if stock <= 0 then
    return 0
end

-- 3. Critical Section: Decrement stock & Record submission (Atomic Operation)
-- Since Lua scripts execute atomically in Redis, no other request can interrupt these two steps.
redis.call('decr', quotaKey)
redis.call('sadd', submittedKey, userId)

return 1 -- Success