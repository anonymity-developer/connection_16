package com.tagme.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Browser-session metadata, never OTP digits or passwords. Reads do not extend deadlines. */
@Component
public class OtpFlowStore {
    public record Flow(String username,String challengeId,long expiresAt,long retryAt,String requestId,
                       int errorStatus,String errorCode,String errorMessage) {
        boolean active(long now){return challengeId!=null&&expiresAt>now;}
        @Override public String toString(){return "OtpFlow[redacted]";}
    }
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
        if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end
        redis.call('SET',KEYS[2],ARGV[2],'EX',ARGV[4])
        redis.call('SET',KEYS[3],ARGV[3],'EX',ARGV[4])
        return 1
        """,Long.class);
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>("""
        if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end
        return 0
        """,Long.class);
    private static final DefaultRedisScript<Long> CLEAR = new DefaultRedisScript<>("""
        local raw=redis.call('GET',KEYS[1])
        if raw and cjson.decode(raw).challengeId==ARGV[1] then return redis.call('DEL',KEYS[1]) end
        return 0
        """,Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final AuthCrypto crypto;
    private final int ttl;
    public OtpFlowStore(StringRedisTemplate redis,ObjectMapper json,AuthCrypto crypto,AuthProperties properties){
        this.redis=redis;this.json=json;this.crypto=crypto;ttl=Math.max(300,properties.otpTtlSeconds())+60;
    }
    private String base(String session,OtpStore.Purpose purpose){return "tagme:auth:flow:v2:"+crypto.digest(session)+":"+purpose;}
    private Flow read(String key){
        String raw=redis.opsForValue().get(key);if(raw==null)return null;
        try{return json.readValue(raw,Flow.class);}catch(JsonProcessingException failure){throw AuthException.unavailable();}
    }
    private String encode(Flow flow){try{return json.writeValueAsString(flow);}catch(JsonProcessingException failure){throw AuthException.unavailable();}}
    public Flow pending(String session,OtpStore.Purpose purpose){return read(base(session,purpose));}
    public Flow receipt(String session,OtpStore.Purpose purpose,String id){return read(base(session,purpose)+":request:"+id);}
    public boolean locked(String session,OtpStore.Purpose purpose){return Boolean.TRUE.equals(redis.hasKey(base(session,purpose)+":lock"));}
    public boolean lock(String session,OtpStore.Purpose purpose,String owner){return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(base(session,purpose)+":lock",owner,Duration.ofSeconds(60)));}
    public void save(String session,OtpStore.Purpose purpose,String owner,String id,Flow current,Flow receipt){
        String base=base(session,purpose);
        if(!Long.valueOf(1).equals(redis.execute(SAVE,List.of(base+":lock",base,base+":request:"+id),owner,encode(current),encode(receipt),Integer.toString(ttl))))
            throw AuthException.unavailable();
    }
    public void unlock(String session,OtpStore.Purpose purpose,String owner){redis.execute(UNLOCK,List.of(base(session,purpose)+":lock"),owner);}
    public void clear(String session,OtpStore.Purpose purpose,String challenge){if(session!=null)redis.execute(CLEAR,List.of(base(session,purpose)),challenge);}
}
