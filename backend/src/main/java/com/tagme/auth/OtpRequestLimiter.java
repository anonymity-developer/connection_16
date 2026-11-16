package com.tagme.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** New issuance attempts only: rolling five-minute quotas and an unchanged resend interval. */
@Component
public class OtpRequestLimiter {
    public record Wait(int seconds, boolean quota) {
        AuthException error() {
            return new AuthException(429, quota ? "OTP_RATE_LIMITED" : "OTP_RESEND_COOLDOWN",
                quota ? "최근 5분간 요청 한도에 도달했어요." : "새 인증번호를 받으려면 잠시 기다려 주세요.", seconds);
        }
    }
    private final StringRedisTemplate redis;
    private final AuthCrypto crypto;
    private final AuthProperties properties;
    private final DefaultRedisScript<List> admission = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> CANCEL = new DefaultRedisScript<>("""
        for i=1,tonumber(ARGV[2])*2,2 do
            redis.call('ZREM',KEYS[i],ARGV[1])
            if redis.call('GET',KEYS[i+1])==ARGV[1] then redis.call('DEL',KEYS[i+1]) end
        end
        return 1
        """,Long.class);

    public OtpRequestLimiter(StringRedisTemplate redis,AuthCrypto crypto,AuthProperties properties) {
        this.redis=redis;this.crypto=crypto;this.properties=properties;
        admission.setLocation(new ClassPathResource("auth/otp-admission.lua"));admission.setResultType(List.class);
    }
    private List<String> keys(String name,String externalId) {
        var keys=new ArrayList<String>();
        for(String subject:externalId==null?List.of("name:"+name):List.of("name:"+name,"account:"+externalId)) {
            String digest=crypto.digest(subject);
            keys.add("tagme:auth:otp:v2:window:"+digest);keys.add("tagme:auth:otp:v2:cooldown:"+digest);
        }
        // Honor only the old 60-second cooldowns during rollout; old 15-minute counters are not reused.
        keys.add("tagme:auth:limit:otp-resend:"+crypto.digest(name));
        if(externalId!=null)keys.add("tagme:auth:cooldown:"+crypto.digest("account:"+externalId));
        return keys;
    }
    private Wait run(String name,String externalId,String id,boolean reserve) {
        var result=redis.execute(admission,keys(name,externalId),reserve?"reserve":"check",id,
            Integer.toString(properties.otpAccountLimit()),"300",Integer.toString(properties.resendSeconds()),externalId==null?"1":"2");
        if(result==null||result.size()!=2)throw AuthException.unavailable();
        return new Wait(((Number)result.get(0)).intValue(),((Number)result.get(1)).intValue()==1);
    }
    public Wait remaining(String name,String externalId){return run(name,externalId,"",false);}
    public void check(String name){var wait=remaining(name,null);if(wait.seconds()>0)throw wait.error();}
    public void reserve(String name,String externalId,String id){var wait=run(name,externalId,id,true);if(wait.seconds()>0)throw wait.error();}
    public void cancel(String name,String externalId,String id){redis.execute(CANCEL,keys(name,externalId),id,externalId==null?"1":"2");}
}
