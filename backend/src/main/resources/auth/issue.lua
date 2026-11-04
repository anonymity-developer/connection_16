if redis.call('EXISTS', KEYS[1]) == 1 then
    return math.max(1, redis.call('TTL', KEYS[1]))
end
local old = redis.call('GET', KEYS[2])
if old then redis.call('DEL', old) end
redis.call('HSET', KEYS[3], 'purpose', ARGV[1], 'externalId', ARGV[2],
    'userId', ARGV[3], 'version', ARGV[4], 'binding', ARGV[5], 'proof', ARGV[6], 'attempts', '0')
redis.call('EXPIRE', KEYS[3], ARGV[7])
redis.call('SET', KEYS[2], KEYS[3], 'EX', ARGV[7])
redis.call('SET', KEYS[1], '1', 'EX', ARGV[8])
return 0
