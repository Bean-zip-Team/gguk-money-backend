local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
local updatedAt = tonumber(redis.call('HGET', KEYS[1], 'updatedAtMillis'))
local capacity = tonumber(ARGV[1])
local refillPerSecond = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

if tokens == nil then
  tokens = capacity
  updatedAt = now
end

local elapsedSeconds = (now - updatedAt) / 1000.0
if elapsedSeconds > 0 then
  tokens = math.min(capacity, tokens + elapsedSeconds * refillPerSecond)
end

local allowed = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
end

redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'updatedAtMillis', tostring(now))
local ttlSeconds = math.ceil(capacity / refillPerSecond) * 2
redis.call('EXPIRE', KEYS[1], ttlSeconds)

return allowed
