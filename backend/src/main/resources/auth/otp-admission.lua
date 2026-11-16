local time=redis.call('TIME')
local now=tonumber(time[1])*1000+math.floor(tonumber(time[2])/1000)
local window=tonumber(ARGV[4])*1000
local groups=tonumber(ARGV[6])
local wait=0
local reason=0
for i=1,groups*2,2 do
    local cutoff=now-window
    local count=redis.call('ZCOUNT',KEYS[i],'('..cutoff,'+inf')
    if count>=tonumber(ARGV[3]) then
        local first=redis.call('ZRANGEBYSCORE',KEYS[i],'('..cutoff,'+inf','WITHSCORES','LIMIT',0,1)
        local seconds=math.max(1,math.ceil((tonumber(first[2])+window-now)/1000))
        if seconds>=wait then wait=seconds;reason=1 end
    end
    local seconds=math.max(0,math.ceil(redis.call('PTTL',KEYS[i+1])/1000))
    if seconds>wait then wait=seconds;reason=2 end
end
for i=groups*2+1,#KEYS do
    local seconds=math.max(0,math.ceil(redis.call('PTTL',KEYS[i])/1000))
    if seconds>wait then wait=seconds;reason=2 end
end
if wait>0 then return {wait,reason} end
if ARGV[1]=='reserve' then
    for i=1,groups*2,2 do
        redis.call('ZREMRANGEBYSCORE',KEYS[i],'-inf',now-window)
        redis.call('ZADD',KEYS[i],now,ARGV[2])
        redis.call('PEXPIRE',KEYS[i],window+1)
        redis.call('SET',KEYS[i+1],ARGV[2],'EX',ARGV[5])
    end
end
return {0,0}
