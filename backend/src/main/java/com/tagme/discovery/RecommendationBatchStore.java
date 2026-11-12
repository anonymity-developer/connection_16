package com.tagme.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.auth.AuthPrincipal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RecommendationBatchStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public RecommendationBatchStore(StringRedisTemplate redis,ObjectMapper json){this.redis=redis;this.json=json;}
    private record Lease(long viewer,long version,List<Long> ids) { }
    public String issue(AuthPrincipal viewer,List<DiscoveryModels.Person> people) {
        String batch=UUID.randomUUID().toString();
        try { redis.opsForValue().set("tagme:recommendation:"+batch,json.writeValueAsString(new Lease(viewer.userId(),viewer.version(),people.stream().map(DiscoveryModels.Person::id).toList())),Duration.ofMinutes(15)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException bad){throw new IllegalStateException(bad);}
        return batch;
    }
    public boolean permits(AuthPrincipal viewer,String batch,List<Long> peers) {
        String value=redis.opsForValue().get("tagme:recommendation:"+batch);
        if(value==null)return false;
        try { var lease=json.readValue(value,Lease.class);return lease.viewer()==viewer.userId()&&lease.version()==viewer.version()&&lease.ids().containsAll(peers); }
        catch(com.fasterxml.jackson.core.JsonProcessingException bad){throw new IllegalStateException(bad);}
    }
}
