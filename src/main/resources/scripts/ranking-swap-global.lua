local tempKey = KEYS[1]
local liveKey = KEYS[2]
local metaKey = KEYS[3]
local lockKey = KEYS[4]
local participantCount = tonumber(ARGV[1])

if redis.call('GET', lockKey) ~= ARGV[8] then
    return -1
end

if participantCount > 0 then
    local tempCount = redis.call('ZCARD', tempKey)
    if tempCount ~= participantCount then
        return 0
    end
    redis.call('DEL', liveKey)
    redis.call('RENAME', tempKey, liveKey)
else
    redis.call('DEL', liveKey)
    redis.call('DEL', tempKey)
end

redis.call('HSET', metaKey,
    'state', 'READY',
    'lastReconciledAt', ARGV[2],
    'lastSuccessfulBuildAt', ARGV[3],
    'participantCount', ARGV[4],
    'schemaVersion', ARGV[5],
    'lastProcessedUpdatedAt', ARGV[6],
    'lastProcessedEntryId', ARGV[7],
    'lastErrorAt', '')

return 1
