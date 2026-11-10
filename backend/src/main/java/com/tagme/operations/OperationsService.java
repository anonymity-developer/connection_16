package com.tagme.operations;

import com.tagme.auth.*;
import com.tagme.content.*;
import com.tagme.participation.ParticipationRepository;
import com.tagme.participation.ParticipationModels.MissionCode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.tagme.content.ContentModels.*;
import static com.tagme.operations.OperationsModels.*;

@Service
public class OperationsService {
    private final OperationsRepository repository;
    private final ContentRepository content;
    private final ReactionRepository reactions;
    private final AdminAuditRepository audit;
    private final AuthRepository accounts;
    private final ParticipationRepository participation;
    private final AuthRateLimiter limits;
    private final TransactionTemplate tx;
    private final TransactionTemplate awardsTx;
    public OperationsService(OperationsRepository repository,ContentRepository content,ReactionRepository reactions,AdminAuditRepository audit,AuthRepository accounts,ParticipationRepository participation,AuthRateLimiter limits,PlatformTransactionManager manager) {
        this.repository=repository;this.content=content;this.reactions=reactions;this.audit=audit;this.accounts=accounts;this.participation=participation;this.limits=limits;this.tx=new TransactionTemplate(manager);
        this.awardsTx=new TransactionTemplate(manager);
        this.awardsTx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.awardsTx.setTimeout(20);
    }
    private static AuthException error(int status,String code,String text) { return new AuthException(status,code,text); }
    private void admin(AuthPrincipal actor) {
        participation.lockPrincipal(actor);
        if(!"ADMIN".equals(accounts.byId(actor.userId()).orElseThrow(AuthException::invalid).role()))throw error(403,"ADMIN_REQUIRED","관리자 권한이 필요해요.");
    }
    public AwardModels.Report awards(AuthPrincipal actor,String category,Integer offset,Integer limit) {
        // Validate current DB role inside the SAME snapshot transaction as the entire ranking.
        return awardsTx.execute(s->{
            admin(actor);
            AwardModels.Category selected;
            try { selected=category==null?AwardModels.Category.FIRST_COMPLETION:AwardModels.Category.valueOf(category); }
            catch(IllegalArgumentException failure) { throw error(400,"INVALID_FILTER","시상 부문을 확인해 주세요."); }
            int start=offset==null?0:offset, count=size(limit);
            if(start<0||start>100000)throw error(400,"INVALID_FILTER","목록 위치를 확인해 주세요.");
            var facts=repository.awardFacts();var ranking=AwardCalculator.rank(facts);var rows=ranking.categories().get(selected);var window=facts.window();
            var reviewKeys=facts.reviews().stream().map(r->r.userId()+":"+r.sourceId()).collect(java.util.stream.Collectors.toSet());
            var reviews=java.util.stream.Stream.concat(facts.reviews().stream(),facts.proofs().stream().filter(p->!p.valid()&&!reviewKeys.contains(p.userId()+":"+p.sourceId())).map(p->new AwardModels.Review(p.userId(),p.sourceId(),"INVALID_"+p.mission()))).toList();
            String phase=window.opensAt()==null?"NOT_CONFIGURED":window.observedAt().isBefore(window.opensAt())?"BEFORE_OPEN"
                :window.observedAt().isBefore(window.closesAt())?"OPEN":!window.observedAt().isAfter(window.moderationClosesAt())?"MODERATION":"REVIEW";
            return new AwardModels.Report(AwardCalculator.RULE_VERSION,true,window.observedAt(),window.opensAt(),window.closesAt(),window.moderationClosesAt(),
                phase,selected,ranking.eligibleCompleteCount(),rows.size(),start,count,rows.stream().skip(start).limit(count).toList(),
                ranking.invalidProofs(),ranking.incompleteProofSets(),facts.pendingModeration(),reviews.size(),reviews.stream().limit(50).toList(),
                List.of("동일 점수·동일 최초 프로필 시각은 공동 순위이며 추가 동점 기준 미확정",
                    "중복 수상 허용 여부·복수 부문 1위의 부문 선택·차순위 배정 방식 미확정",
                    "0댓글·0하트인 경우 최소 활동 기준 미확정: 점수 0 그대로 표시",
                    "랜덤 참여상 인원·적격 조건 미확정: 추첨하지 않음",
                    "최종 스냅샷 확정·정정·공개·지급 정책 미확정: 잠정 조회만 제공"));
        });
    }
    public Access access(AuthPrincipal actor) { return tx.execute(s->{admin(actor);return new Access(true);}); }
    private int size(Integer limit) { int size=limit==null?20:limit;if(size<1||size>50)throw error(400,"INVALID_FILTER","한 번에 최대 50건까지 확인할 수 있어요.");return size; }
    private long cursor(Long cursor,long fallback) { if(cursor!=null&&cursor<1)throw error(400,"INVALID_FILTER","목록 위치를 확인해 주세요.");return cursor==null?fallback:cursor; }
    private String filter(String value,List<String> allowed) { if(value!=null&&!allowed.contains(value))throw error(400,"INVALID_FILTER","목록 조건을 확인해 주세요.");return value; }
    private static void reason(String reason) { if(reason==null||reason.isBlank()||reason.length()>300)throw error(400,"INVALID_REASON","조치 사유를 1~300자로 입력해 주세요."); }
    public Page<Task> tasks(AuthPrincipal actor,Long after,Integer limit,String status) {
        access(actor);int size=size(limit);long start=cursor(after,0);String state=filter(status==null?"PENDING":status,List.of("PENDING","QUEUED","PROCESSING","REVIEW_REQUIRED","APPROVED","REJECTED","SUPERSEDED","ALL"));
        return tx.execute(s->{admin(actor);var ids=repository.taskIds(start,size+1,"ALL".equals(state)?null:state);var rows=ids.stream().limit(size).map(id->{var job=content.job(id,false);var item=content.item(job.itemId(),false);return new Task(new ReviewTask(job,content.revision(job.revisionId()),item.approvedId()==null?null:content.revision(item.approvedId())),item.kind(),item.visibility(),item.version(),repository.retryCount(id));}).toList();return new Page<>(rows,ids.size()>size?ids.get(size-1):null);});
    }
    public Task task(AuthPrincipal actor,long id) {
        return tx.execute(s->{admin(actor);var job=content.job(id,false);if(job==null)throw error(404,"JOB_NOT_FOUND","검수 작업을 찾을 수 없어요.");var item=content.item(job.itemId(),false);return new Task(new ReviewTask(job,content.revision(job.revisionId()),item.approvedId()==null?null:content.revision(item.approvedId())),item.kind(),item.visibility(),item.version(),repository.retryCount(id));});
    }
    public Content detail(AuthPrincipal actor,long id) {
        return tx.execute(s->{admin(actor);var item=content.item(id,false);if(item==null)throw error(404,"CONTENT_NOT_FOUND","글을 찾을 수 없어요.");return snapshot(item);});
    }
    private Content snapshot(Item item) {
        var comment=item.kind()==Kind.COMMENT?reactions.byItem(item.id()):null;
        return new Content(item.id(),item.kind(),item.authorId(),item.visibility(),item.version(),comment==null?null:comment.wishId(),item.approvedId()==null?null:content.revision(item.approvedId()),item.pendingId()==null?null:content.revision(item.pendingId()),content.latest(item.id()));
    }
    public Page<Content> contents(AuthPrincipal actor,Long cursor,Integer limit,String kind,String visibility) {
        access(actor);int size=size(limit);long before=cursor(cursor,Long.MAX_VALUE);String k=filter(kind,List.of("WISH","CHEER","COMMENT")),v=filter(visibility,List.of("VISIBLE","HIDDEN"));
        return tx.execute(s->{admin(actor);var ids=repository.contentIds(before,size+1,k,v);var rows=ids.stream().limit(size).map(id->snapshot(content.item(id,false))).toList();return new Page<>(rows,ids.size()>size?ids.get(size-1):null);});
    }
    public Page<Audit> audits(AuthPrincipal actor,Long cursor,Integer limit,String action) {
        access(actor);int size=size(limit);long before=cursor(cursor,Long.MAX_VALUE);String a=filter(action,List.of("MODERATION_DECISION","MODERATION_RETRY","CONTENT_VISIBILITY"));
        return tx.execute(s->{admin(actor);var rows=audit.list(before,size+1,a);return new Page<>(rows.stream().limit(size).toList(),rows.size()>size?rows.get(size-1).id():null);});
    }
    private long[] owners(AuthPrincipal actor,Item item) {
        var comment=item.kind()==Kind.COMMENT?reactions.byItem(item.id()):null;
        return java.util.stream.LongStream.of(actor.userId(),item.authorId(),comment==null?item.authorId():comment.wishAuthorId()).distinct().sorted().toArray();
    }
    private void mutateGuard(AuthPrincipal actor,long[] owners) { for(long id:owners)accounts.lockUser(id);admin(actor); }
    public void visibility(AuthPrincipal actor,long id,long expectedVersion,String next,String reason) {
        access(actor);reason(reason);filter(next,List.of("VISIBLE","HIDDEN"));if(next==null||expectedVersion<1)throw error(400,"INVALID_VISIBILITY","공개 상태와 버전을 확인해 주세요.");
        var selected=content.item(id,false);if(selected==null)throw error(404,"CONTENT_NOT_FOUND","글을 찾을 수 없어요.");long[] owners=owners(actor,selected);
        limits.check("admin-operation",Long.toString(actor.userId()),60,60);
        tx.executeWithoutResult(s->{mutateGuard(actor,owners);var item=content.item(id,true);if(item.version()!=expectedVersion)throw error(409,"CONTENT_CHANGED","글 상태가 바뀌었어요. 다시 불러와 주세요.");if(item.visibility().equals(next))return;
            var before=AdminAuditRepository.state(item,null);repository.visibility(item,next);audit.record(actor.userId(),"CONTENT_VISIBILITY","CONTENT",id,reason,before,AdminAuditRepository.state(content.item(id,false),null));
        });
    }
    public void retry(AuthPrincipal actor,long id,long expectedFence,String reason) {
        access(actor);reason(reason);if(expectedFence<0)throw error(400,"INVALID_RETRY","검수 버전을 확인해 주세요.");var selected=content.job(id,false);if(selected==null)throw error(404,"JOB_NOT_FOUND","검수 작업을 찾을 수 없어요.");long[] owners=owners(actor,content.item(selected.itemId(),false));
        limits.check("admin-operation",Long.toString(actor.userId()),60,60);
        tx.executeWithoutResult(s->{mutateGuard(actor,owners);var item=content.item(selected.itemId(),true);var job=content.job(id,true);
            if(job.fence()!=expectedFence||!"REVIEW_REQUIRED".equals(job.status())||!Objects.equals(item.pendingId(),job.revisionId()))throw error(409,"REVIEW_CHANGED","현재 재처리 가능한 버전이 아니에요. 다시 불러와 주세요.");
            if(!"VISIBLE".equals(item.visibility()))throw error(409,"CONTENT_HIDDEN","숨김을 해제한 뒤 재처리해 주세요.");
            if(!content.inModerationWindow())throw error(403,"MODERATION_CLOSED","검수 가능한 기간이 아니에요.");
            var mission=participation.mission(item.authorId(),item.kind()==Kind.COMMENT?MissionCode.MAKE_A_WISH:item.kind().mission());
            var account=accounts.byId(item.authorId()).orElseThrow(AuthException::invalid);
            var parent=item.kind()==Kind.COMMENT?reactions.byItem(item.id()):null;
            if(!"ACTIVE".equals(account.status())||!participation.eligible(item.authorId())||!mission.active()||"LOCKED".equals(mission.state())||(item.kind()==Kind.COMMENT&&(parent==null||!reactions.visible(parent.wishId(),Kind.WISH))))throw error(409,"PARTICIPATION_UNAVAILABLE","작성자의 현재 참여 조건을 확인해 주세요.");
            int count=repository.retryCount(id);if(count>=3)throw error(409,"RETRY_LIMIT","수동 재처리는 최대 3회예요. 원문을 확인하고 판정해 주세요.");
            var before=AdminAuditRepository.state(item,job);before.put("retryCount",count);repository.retry(job);var after=AdminAuditRepository.state(content.item(item.id(),false),content.job(id,false));after.put("retryCount",count+1);audit.record(actor.userId(),"MODERATION_RETRY","MODERATION_JOB",id,reason,before,after);
        });
    }
}
