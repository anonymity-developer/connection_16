package com.tagme.content;

import com.tagme.auth.*;
import com.tagme.operations.AdminAuditRepository;
import com.tagme.participation.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.tagme.content.ContentModels.*;
import static com.tagme.content.ContentService.error;

@Service
public class ModerationService {
    private final ContentRepository repository;
    private final ParticipationRepository participation;
    private final MissionCompletionService completion;
    private final AuthRepository accounts;
    private final TransactionTemplate tx;
    private final ModerationProvider provider;
    private final ReactionRepository reactions;
    private final AdminAuditRepository audit;
    private static final Pattern CONTACT=Pattern.compile("(?i)(?:[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}|0\\d{1,2}[- .]?\\d{3,4}[- .]?\\d{4}|https?://|www\\.)");
    public ModerationService(ContentRepository repository,ParticipationRepository participation,MissionCompletionService completion,AuthRepository accounts,PlatformTransactionManager manager,ModerationProvider provider,ReactionRepository reactions,AdminAuditRepository audit) {
        this.repository=repository;this.participation=participation;this.completion=completion;this.accounts=accounts;this.tx=new TransactionTemplate(manager);this.provider=provider;this.reactions=reactions;this.audit=audit;
    }
    public Work claim() { return tx.execute(s->repository.claim()); }
    public void processOne() {
        Work work=claim();if(work==null)return;
        if(work.job().attempts()>3){apply(work,new ModerationProvider.Decision("REVIEW_REQUIRED","RETRY_EXHAUSTED","SYSTEM","rules-v1"));return;}
        try {
            // External work must be outside every DB transaction/row lock.
            var decision=CONTACT.matcher(work.text()).find()?new ModerationProvider.Decision("REVIEW_REQUIRED","CONTACT_OR_URL","SYSTEM","rules-v1"):provider.inspect(work.text());
            if(decision==null||!List.of("APPROVED","REJECTED","REVIEW_REQUIRED").contains(decision.status())||!token(decision.reason(),40)||!token(decision.provider(),40)||!token(decision.policyVersion(),64))
                decision=new ModerationProvider.Decision("REVIEW_REQUIRED","INVALID_PROVIDER_RESPONSE","SYSTEM","rules-v1");
            apply(work,decision);
        } catch(ModerationProvider.TemporaryFailure temporary) { fail(work,true); }
        catch(Exception failure) { fail(work,false); } // never store exception/provider bodies or text in error logs
    }
    private boolean token(String value,int max) { return value!=null&&value.length()<=max&&value.matches("[A-Za-z0-9._-]+"); }
    private Job current(Work work) {
        var item=repository.item(work.job().itemId(),true);var job=repository.job(work.job().id(),true);
        if(job==null||job.fence()!=work.job().fence()||!"PROCESSING".equals(job.status())||job.leaseUntil()==null||!job.leaseUntil().isAfter(repository.now())||item.pendingId()==null||item.pendingId()!=job.revisionId())return null;
        return job;
    }
    public void apply(Work work,ModerationProvider.Decision decision) {
        long[] owners=owners(work.job().authorId(),work.job().itemId(),null);
        tx.executeWithoutResult(s->{
            // Work's author is persisted and cannot be edited by a client.
            if(!lockOwners(owners,work.job().authorId()))return;
            var job=current(work);if(job==null)return;
            decide(job,decision.status(),decision.reason(),decision.reason(),decision.provider(),decision.policyVersion(),null);
        });
    }
    private long[] owners(long author,long item,Long admin) {
        // Parent linkage is immutable. Discover IDs before beginning the transaction's snapshot.
        var comment=reactions.byItem(item);
        return java.util.stream.LongStream.of(author,comment==null?author:comment.wishAuthorId(),admin==null?author:admin).distinct().sorted().toArray();
    }
    private boolean lockOwners(long[] ids,long author) {
        for(long id:ids)accounts.lockUser(id);
        return accounts.byId(author).isPresent();
    }
    private void fail(Work work,boolean temporary) {
        long[] owners=owners(work.job().authorId(),work.job().itemId(),null);
        tx.executeWithoutResult(s->{
            if(!lockOwners(owners,work.job().authorId()))return;var job=current(work);if(job==null)return;
            if(temporary&&job.attempts()<3)repository.retry(job,"PROVIDER_TEMPORARY");
            else repository.decision(job,"REVIEW_REQUIRED",temporary?"RETRY_EXHAUSTED":"PROVIDER_FAILURE",temporary?"RETRY_EXHAUSTED":"PROVIDER_FAILURE","SYSTEM","rules-v1",null);
        });
    }
    private void decide(Job job,String decision,String code,String detail,String providerName,String policy,Long admin) {
        var item=repository.item(job.itemId(),false);
        if("APPROVED".equals(decision)) {
            var mission=participation.mission(job.authorId(),item.kind()==Kind.COMMENT?com.tagme.participation.ParticipationModels.MissionCode.MAKE_A_WISH:item.kind().mission());
            var account=accounts.byId(job.authorId()).orElseThrow(AuthException::invalid);
            boolean eligible="ACTIVE".equals(account.status())&&repository.inModerationWindow()&&"VISIBLE".equals(item.visibility())&&mission.active()&&!"LOCKED".equals(mission.state())&&participation.eligible(job.authorId());
            if(item.kind()==Kind.COMMENT) {
                var parent=reactions.byItem(item.id());var event=participation.event(true);var submitted=repository.revision(job.revisionId()).submittedAt();
                eligible=eligible&&parent!=null&&reactions.visible(parent.wishId(),Kind.WISH)&&event.opensAt()!=null&&!submitted.isBefore(event.opensAt())&&submitted.isBefore(event.submitClosesAt());
            }
            if(!eligible){decision="REVIEW_REQUIRED";code="PARTICIPATION_UNAVAILABLE";detail=code;}
        }
        repository.decision(job,decision,code,detail,providerName,policy,admin);
        if("APPROVED".equals(decision)&&item.kind()!=Kind.COMMENT)completion.completeModerated(job.authorId(),item.kind().mission(),repository.receiptId(job.authorId(),item.kind().source(),job.revisionId()));
    }
    private void admin(AuthPrincipal actor) {
        participation.lockPrincipal(actor);
        if(!"ADMIN".equals(accounts.byId(actor.userId()).orElseThrow(AuthException::invalid).role()))throw error(403,"ADMIN_REQUIRED","관리자 권한이 필요해요.");
    }
    public List<ReviewTask> queue(AuthPrincipal actor) { return tx.execute(s->{admin(actor);return repository.reviewQueue().stream().map(j->{var i=repository.item(j.itemId(),false);return new ReviewTask(j,repository.revision(j.revisionId()),i.approvedId()==null?null:repository.revision(i.approvedId()));}).toList();}); }
    public OwnSlot review(AuthPrincipal actor,long jobId,ReviewRequest body) {
        if(body.reason()==null||body.reason().isBlank()||body.reason().length()>300||!List.of("APPROVED","REJECTED").contains(body.decision()))throw error(400,"INVALID_REVIEW","판정과 사유를 확인해 주세요.");
        tx.executeWithoutResult(s->admin(actor));
        var selected=repository.job(jobId,false);if(selected==null)throw error(404,"JOB_NOT_FOUND","검수 작업을 찾을 수 없어요.");
        long[] owners=owners(selected.authorId(),selected.itemId(),actor.userId());
        return tx.execute(s->{
            // Admin/author/parent IDs ascending, before all content and global completion locks.
            lockOwners(owners,selected.authorId());admin(actor);
            var item=repository.item(selected.itemId(),true);var job=repository.job(jobId,true);
            if(!List.of("QUEUED","PROCESSING","REVIEW_REQUIRED").contains(job.status()))throw error(409,"REVIEW_CHANGED","이미 처리한 버전이에요. 다시 불러와 주세요.");
            if(job.fence()!=body.expectedFence()||!Objects.equals(item.pendingId(),job.revisionId()))throw error(409,"REVIEW_CHANGED","검수 상태가 변경됐어요. 다시 불러와 주세요.");
            if(!repository.inModerationWindow())throw error(403,"MODERATION_CLOSED","검수 가능한 기간이 아니에요.");
            var before=AdminAuditRepository.state(item,job);
            job=repository.fenceManual(job);decide(job,body.decision(),"ADMIN_DECISION",body.reason().strip(),"ADMIN","manual-v1",actor.userId());
            var after=AdminAuditRepository.state(repository.item(item.id(),false),repository.job(jobId,false));
            after.put("requestedDecision",body.decision());
            audit.record(actor.userId(),"MODERATION_DECISION","MODERATION_JOB",jobId,body.reason(),before,after);
            // Return no body containing pending text through a public resource.
            return null;
        });
    }
}
