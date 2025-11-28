-- Key to operate on
local key = KEYS[1]

-- Arguments from application
local capacity = tonumber(ARGV[1])
local refill_rate_per_second = tonumber(ARGV[2])
local current_time_ms = tonumber(ARGV[3])
local requested_tokens = 1 -- Hardcoded to 1 for API requests

-- Get current state
local bucket = redis.call('hgetall', key)
local last_refilled_ms = 0
local tokens = capacity

if #bucket > 0 then
    -- Bucket exists, parse its state
    last_refilled_ms = tonumber(bucket[4]) -- 'last_refilled_ms'
    tokens = tonumber(bucket[2]) -- 'tokens'
end

-- Calculate tokens to add since last refill
local elapsed_ms = current_time_ms - last_refilled_ms
local tokens_to_add = math.floor((elapsed_ms / 1000) * refill_rate_per_second)

if tokens_to_add > 0 then
    -- Add new tokens, capped at capacity
    tokens = math.min(tokens + tokens_to_add, capacity)
    last_refilled_ms = current_time_ms
end

-- Check if enough tokens are available
if tokens >= requested_tokens then
    -- Consume token and update the bucket
    local new_tokens = tokens - requested_tokens
    redis.call('hmset', key, 'tokens', new_tokens, 'last_refilled_ms', last_refilled_ms)
    redis.call('expire', key, 300) -- Set 5-min TTL
    return {1, new_tokens} -- { Allowed (true), Remaining Tokens }
else
    return {0, tokens} -- { Allowed (false), Remaining Tokens }
end