package com.tagme.discovery;

import com.tagme.auth.*;
import com.tagme.participation.ParticipationRepository;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.tagme.discovery.DiscoveryModels.*;

@Service
public class DiscoveryService {
    private final DiscoveryRepository repository;
    private final ParticipationRepository participation;
    private final AuthRepository accounts;
    private final AuthRateLimiter limits;
    private final TransactionTemplate tx;
    private final RecommendationBatchStore batches;
    public DiscoveryService(DiscoveryRepository repository,ParticipationRepository participation,AuthRepository accounts,AuthRateLimiter limits,PlatformTransactionManager manager,RecommendationBatchStore batches) { this.repository=repository;this.participation=participation;this.accounts=accounts;this.limits=limits;this.tx=new TransactionTemplate(manager);this.batches=batches; }
    static AuthException error(int status,String code,String message) { return new AuthException(status,code,message); }
    private void guard(AuthPrincipal actor) {
        participation.lockPrincipal(actor);
        if(!participation.eligible(actor.userId())||!participation.discoveryReady(actor.userId()))throw error(409,"PROFILE_REQUIRED","한 줄 소개와 관심 산업·개발 분야·기술 스택을 먼저 완성해 주세요.");
    }
    public People people(AuthPrincipal actor,Long cursor,Integer limit,String q,Long interest,String type,String version) {
        return people(actor,cursor,limit,q,interest,type,version,List.of(),List.of(),List.of());
    }
    public People people(AuthPrincipal actor,Long cursor,Integer limit,String q,Long interest,String type,String version,List<Long> industries,List<Long> developments,List<Long> stacks) {
        int size=limit==null?20:limit;
        if((cursor!=null&&cursor<1)||size<1||size>30||(q!=null&&q.length()>30)||(interest!=null&&interest<1)
          ||(type==null)!=(version==null)||(type!=null&&(!type.matches("[A-Z]{1,4}")||!version.matches("[A-Za-z0-9._-]{1,64}"))))throw error(400,"INVALID_FILTER","검색어나 목록 조건을 확인해 주세요.");
        var filters=List.of(industries==null?List.<Long>of():industries,developments==null?List.<Long>of():developments,stacks==null?List.<Long>of():stacks);
        for(var filter:filters)if(filter.size()>50||filter.stream().anyMatch(id->id==null||id<1)||filter.stream().distinct().count()!=filter.size())throw error(400,"INVALID_FILTER","관심사 검색 조건을 다시 확인해 주세요.");
        return tx.execute(s->{guard(actor);return repository.people(actor.userId(),cursor==null?Long.MAX_VALUE:cursor,size,q,interest,type,version,filters.get(0),filters.get(1),filters.get(2));});
    }
    public RecommendationBatch batch(AuthPrincipal actor,String seed) {
        limits.check("recommendation-batch",Long.toString(actor.userId()),30,60);
        var people=recommended(actor,seed);
        return new RecommendationBatch(batches.issue(actor,people),people);
    }
    public void expose(AuthPrincipal actor,String batch,List<Long> peers) {
        limits.check("recommendation-exposure",Long.toString(actor.userId()),120,60);
        if(batch==null||!batch.matches("[0-9a-f-]{36}")||peers==null||peers.isEmpty()||peers.size()>12||peers.stream().anyMatch(id->id==null||id<1)||peers.stream().distinct().count()!=peers.size())throw error(400,"INVALID_EXPOSURE","추천 목록을 다시 확인해 주세요.");
        if(!batches.permits(actor,batch,peers))throw error(409,"RECOMMENDATION_BATCH_EXPIRED","추천 목록이 변경됐어요. 다시 불러와 주세요.");
        tx.executeWithoutResult(s->{guard(actor);repository.expose(actor.userId(),batch,peers);});
    }
    public List<Person> recommended(AuthPrincipal actor) { return recommended(actor,null); }
    public List<Person> recommended(AuthPrincipal actor,String seed) {
        if(seed!=null&&!seed.matches("[A-Za-z0-9_-]{1,64}"))throw error(400,"INVALID_RECOMMENDATION_SEED","추천 목록을 다시 열어 주세요.");
        return tx.execute(s->{guard(actor);return repository.recommended(actor.userId(),seed==null?"initial":seed);});
    }
    public Detail open(AuthPrincipal actor,long id) {
        if(id<1)throw error(404,"PROFILE_NOT_FOUND","공개된 프로필을 찾을 수 없어요.");
        limits.check("profile-open-user",Long.toString(actor.userId()),60,60);
        return tx.execute(s->{
            java.util.stream.LongStream.of(actor.userId(),id).distinct().sorted().forEach(accounts::lockUser);guard(actor);
            participation.requireOpen(false);
            var person=repository.person(actor.userId(),id);if(person==null)throw error(404,"PROFILE_NOT_FOUND","공개된 프로필을 찾을 수 없어요.");
            boolean created=repository.visit(actor.userId(),id);var fields=repository.fields(id);person=repository.person(actor.userId(),id);
            return new Detail(person,fields.realName(),fields.region(),fields.classNo(),fields.bio(),safeUrl(fields.githubUrl()),safeUrl(fields.blogUrl()),fields.photoUrl(),created);
        });
    }
    static String safeUrl(String text) {
        if(text==null||text.length()>2048)return null;
        try{var uri=URI.create(text);return "https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null?text:null;}catch(IllegalArgumentException invalid){return null;}
    }
    public Stats stats(AuthPrincipal actor) { return tx.execute(s->{guard(actor);return repository.stats(actor.userId());}); }
    public Graph graph(AuthPrincipal actor,Integer limit) {
        return graph(actor,limit,0);
    }
    public Graph graph(AuthPrincipal actor,Integer limit,int offset) {
        return graph(actor,limit,offset,"all");
    }
    public Graph graph(AuthPrincipal actor,Integer limit,int offset,String relationship) {
        int size=limit==null?20:limit;if(offset<0||size<1||size>30)throw error(400,"INVALID_LIMIT","관계는 한 번에 최대 30명까지 확인할 수 있어요.");
        if(!List.of("all","visited","visitors","mutual").contains(relationship))throw error(400,"INVALID_FILTER","관계 검색 조건을 다시 확인해 주세요.");
        return tx.execute(s->{guard(actor);return repository.graph(actor.userId(),participation.profile(actor.userId()).nickname(),size,offset,relationship);});
    }
}
