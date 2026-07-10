if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
local currentJti = redis.call('HGET', KEYS[1], 'currentRefreshJtiHash')
local currentToken = redis.call('HGET', KEYS[1], 'refreshTokenHash')
local previousJti = redis.call('HGET', KEYS[1], 'previousRefreshJtiHash')
local rotatedAtMillis = redis.call('HGET', KEYS[1], 'rotatedAtEpochMillis')
if currentJti ~= ARGV[1] or currentToken ~= ARGV[2] then
  if previousJti == ARGV[1] then
    if rotatedAtMillis and rotatedAtMillis ~= '' then
      local elapsedMillis = tonumber(ARGV[9]) - tonumber(rotatedAtMillis)
      if elapsedMillis >= 0 and elapsedMillis <= tonumber(ARGV[10]) then
        return 2
      end
    end
    return 3
  end
  return 2
end
redis.call('HSET', KEYS[1],
  'previousRefreshJtiHash', currentJti,
  'currentRefreshJtiHash', ARGV[3],
  'refreshTokenHash', ARGV[4],
  'rotatedAt', ARGV[5],
  'rotatedAtEpochMillis', ARGV[9],
  'expiresAt', ARGV[6],
  'status', 'ACTIVE')
redis.call('PEXPIREAT', KEYS[1], ARGV[7])
redis.call('ZADD', KEYS[2], ARGV[7], ARGV[8])
return 1
