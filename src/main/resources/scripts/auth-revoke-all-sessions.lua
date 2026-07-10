redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
local sessionIds = redis.call('ZRANGE', KEYS[1], 0, -1)
local revokedCount = 0
for _, sessionId in ipairs(sessionIds) do
  local refreshKey = 'auth:refresh:' .. sessionId
  if redis.call('EXISTS', refreshKey) == 1 then
    redis.call('DEL', refreshKey)
    revokedCount = revokedCount + 1
  end
end
redis.call('DEL', KEYS[1])
redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
if ARGV[4] and ARGV[4] ~= '' and ARGV[5] and ARGV[5] ~= '' then
  local denyTtl = tonumber(ARGV[5]) - tonumber(ARGV[1])
  if denyTtl > 0 then
    redis.call('SET', 'auth:deny:access:' .. ARGV[4], '1', 'PX', denyTtl)
  end
end
return revokedCount
