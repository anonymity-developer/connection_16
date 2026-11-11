package com.tagme.content;

import com.tagme.auth.*;
import com.tagme.participation.*;
import com.tagme.results.ResultsService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Converts only existing submissions; never creates a participant or a game event. */
@Service
public class PendingSubmissionSettlement {
    private final JdbcTemplate db;private final ContentRepository content;private final ReactionRepository reactions;
    private final AuthRepository accounts;private final ParticipationRepository participation;private final MissionCompletionService completion;
    private final PublicationPolicy policy;private final ResultsService results;private final TransactionTemplate tx;
    public PendingSubmissionSettlement(JdbcTemplate db,ContentRepository content,ReactionRepository reactions,AuthRepository accounts,
        ParticipationRepository participation,MissionCompletionService completion,PublicationPolicy policy,ResultsService results,PlatformTransactionManager manager) {
        this.db=db;this.content=content;this.reactions=reactions;this.accounts=accounts;this.participation=participation;this.completion=completion;this.policy=policy;this.results=results;this.tx=new TransactionTemplate(manager);
    }
    public void settleContent(long id) {
        if(policy.moderated())return;
        var selected=content.item(id,false);if(selected==null)return;
        var parent=reactions.byItem(id);
        long[] owners=java.util.stream.LongStream.of(selected.authorId(),parent==null?selected.authorId():parent.wishAuthorId()).distinct().sorted().toArray();
        tx.executeWithoutResult(s->{
            for(long user:owners)accounts.lockUser(user);
            var item=content.item(id,true);if(item==null||item.pendingId()==null||!"VISIBLE".equals(item.visibility()))return;
            var revision=content.revision(item.pendingId());
            if(!List.of("QUEUED","PROCESSING","REVIEW_REQUIRED").contains(revision.status()))return;
            var receipts=db.queryForList("SELECT id FROM submission_receipts WHERE user_id=? AND source_kind=? AND source_id=?",Long.class,item.authorId(),item.kind().source(),revision.id());
            if(receipts.isEmpty())return;
            content.publish(item,revision.id());
            if(item.kind()==ContentModels.Kind.COMMENT)return;
            var mission=participation.mission(item.authorId(),item.kind().mission());var event=participation.event(true);
            if(mission.active()&&!"LOCKED".equals(mission.state())&&participation.eligible(item.authorId())&&content.inModerationWindow()
                &&event.opensAt()!=null&&!revision.submittedAt().isBefore(event.opensAt())&&revision.submittedAt().isBefore(event.submitClosesAt()))
                completion.completeModerated(item.authorId(),item.kind().mission(),receipts.getFirst());
        });
    }
    public void processBatch() {
        if(policy.moderated())return;
        for(long id:db.queryForList("SELECT id FROM content_items WHERE archived_at IS NULL AND pending_revision_id IS NOT NULL AND visibility_status='VISIBLE' ORDER BY id LIMIT 20",Long.class))settleContent(id);
        var taps=db.queryForList("SELECT t.id,t.user_id FROM tap_slots s JOIN tap_attempts t ON t.id=s.current_attempt_id WHERE t.archived_at IS NULL AND t.status='REVIEW_REQUIRED' ORDER BY t.id LIMIT 20");
        for(var tap:taps)try{results.settleReview(((Number)tap.get("user_id")).longValue(),((Number)tap.get("id")).longValue());}
            catch(AuthException unavailable){ /* Closed/ineligible accounts never receive a fabricated completion. */ }
    }
}
