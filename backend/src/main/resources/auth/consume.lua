if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
if redis.call('HGET', KEYS[1], 'purpose') ~= ARGV[1]
    or redis.call('HGET', KEYS[1], 'binding') ~= ARGV[2] then return 0 end
local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
if attempts > tonumber(ARGV[4]) then redis.call('DEL', KEYS[1]) return 0 end
if redis.call('HGET', KEYS[1], 'proof') ~= ARGV[3] then
    if attempts >= tonumber(ARGV[4]) then redis.call('DEL', KEYS[1]) end
    return 0
end
redis.call('DEL', KEYS[1])
return 1
