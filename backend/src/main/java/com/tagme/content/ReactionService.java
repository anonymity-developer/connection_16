package com.tagme.content;

import com.tagme.auth.*;
import com.tagme.participation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.tagme.content.ContentModels.*;
import static com.tagme.content.ReactionRepository.*;
import static com.tagme.participation.ParticipationModels.MissionCode;
import static com.tagme.content.ContentService.error;

@Service
public class ReactionService {
    private final ReactionRepository repository;
    private final ContentRepository content;
    private final ParticipationRepository participation;
    private final AuthRepository accounts;
    private final AuthRateLimiter limits;
    private final TransactionTemplate tx;
    private final PublicationPolicy publication;
    public ReactionService(ReactionRepository repository,ContentRepository content,ParticipationRepository participation,AuthRepository accounts,AuthRateLimiter limits,PlatformTransactionManager manager,PublicationPolicy publication) { this.repository=repository;this.content=content;this.participation=participation;this.accounts=accounts;this.limits=limits;this.tx=new TransactionTemplate(manager);this.publication=publication; }
    private Item target(long id,Kind kind) { var item=content.item(id,false);if(item==null||item.kind()!=kind)throw missing();return item; }
    static AuthException missing() { return error(404,"CONTENT_NOT_FOUND","공개된 글을 찾을 수 없어요."); }
    private long cursor(Long cursor) { if(cursor!=null&&cursor<1)throw error(400,"INVALID_CURSOR","목록 위치를 확인해 주세요.");return cursor==null?Long.MAX_VALUE:cursor; }
    private void lock(AuthPrincipal actor,Item target) { java.util.stream.LongStream.of(actor.userId(),target.authorId()).distinct().sorted().forEach(accounts::lockUser);participation.lockPrincipal(actor); }
    private void visible(Item target) { if(!repository.visible(target.id(),target.kind()))throw missing(); }
    private boolean unlocked(long user,MissionCode code) { var mission=participation.mission(user,code);participation.activeMission(mission.id());return !"LOCKED".equals(mission.state()); }
    private void writeGuard(AuthPrincipal actor,MissionCode code) {
        participation.requireOpen(false);
        if(!unlocked(actor.userId(),code))throw error(403,"MISSION_LOCKED","먼저 현장 태그로 미션을 열어주세요.");
        if(!participation.eligible(actor.userId()))throw error(409,"PROFILE_REQUIRED","프로필과 현재 공개 동의를 확인해 주세요.");
    }
    public Comments comments(long wish,Long cursor) { var item=target(wish,Kind.WISH);visible(item);return repository.comments(wish,cursor(cursor)); }
    // Own statuses remain readable if the parent becomes private; never return its body/author.
    public OwnComments own(long user,long wish,Long cursor) { return repository.own(wish,user,cursor(cursor)); }
    private String hash(long wish,String text,boolean reveal) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((wish+"|"+reveal+"|"+text).getBytes(StandardCharsets.UTF_8))); }catch(Exception impossible){throw new IllegalStateException(impossible);} }
    public OwnComment submit(AuthPrincipal actor,long wish,String key,ReactionController.CommentRequest body) {
        return submit(actor,wish,key,body,0);
    }
    public OwnComment submit(AuthPrincipal actor,long wish,String key,ReactionController.CommentRequest body,long generation) {
        if(key==null||!key.matches("[A-Za-z0-9_-]{16,64}"))throw error(400,"INVALID_IDEMPOTENCY_KEY","요청 식별자를 확인해 주세요.");
        if(body.content()==null||body.content().isBlank()||body.content().length()>200||body.revealProfile()==null)throw error(400,"INVALID_COMMENT","댓글 내용을 확인해 주세요.");
        limits.check("comment-user",Long.toString(actor.userId()),30,60);String text=body.content().strip(),hash=hash(wish,text,body.revealProfile());var target=target(wish,Kind.WISH);var received=content.now();
        return tx.execute(s->{
            lock(actor,target);participation.requireGeneration(actor.userId(),generation);var replay=repository.byKey(actor.userId(),key);
            if(replay!=null&&content.item(replay.itemId(),false)==null)throw error(409,"PARTICIPATION_RESET","초기화 전 요청이에요. 화면을 새로고침해 주세요.");
            if(replay!=null){if(!hash.equals(replay.hash())||wish!=replay.wishId())throw error(409,"IDEMPOTENCY_CONFLICT","같은 요청의 내용이 변경됐어요.");return ownComment(replay.id(),content.latest(replay.itemId()),key);}
            writeGuard(actor,MissionCode.MAKE_A_WISH);visible(target);
            var event=participation.event(true);if(received.isBefore(event.opensAt())||!received.isBefore(event.submitClosesAt()))throw error(403,"EVENT_CLOSED","지금은 댓글을 남길 수 없어요.");
            long itemId=content.create(actor.userId(),Kind.COMMENT,null,null);var item=content.item(itemId,true);
            long revision=content.addRevision(itemId,1,new ContentController.Submit(text,"PLAIN","ROSE",!body.revealProfile(),null),key,hash,received);
            if(publication.moderated())content.queue(item,revision);else content.publish(item,revision);
            long id=repository.saveComment(wish,itemId,actor.userId(),key,hash);participation.receipt(actor.userId(),Kind.COMMENT.source(),revision,received);
            return ownComment(id,content.revision(revision),key);
        });
    }
    private OwnComment ownComment(long id,Revision r,String key) { return new OwnComment(id,r.content(),!r.anonymous(),r.status(),r.reason(),r.submittedAt(),key); }
    public Map<Long,LikeState> states(AuthPrincipal actor,List<Long> ids) {
        if(ids==null||ids.isEmpty()||ids.size()>20||ids.stream().anyMatch(id->id==null||id<1)||ids.stream().distinct().count()!=ids.size())throw error(400,"INVALID_CARD_IDS","한 번에 최대 20개의 서로 다른 카드를 확인해 주세요.");
        return tx.execute(s->{participation.lockPrincipal(actor);var mission=participation.mission(actor.userId(),MissionCode.SAY_SOMETHING);
            boolean allowed="OPEN".equals(participation.event(true).status())&&mission.active()&&!"LOCKED".equals(mission.state())&&participation.eligible(actor.userId());
            return repository.states(ids,actor.userId(),allowed);
        });
    }
    public LikeState state(AuthPrincipal actor,long card) { var target=target(card,Kind.CHEER);return tx.execute(s->{lock(actor,target);visible(target);return stateLocked(actor,target);}); }
    private LikeState stateLocked(AuthPrincipal actor,Item target) {
        var mission=participation.mission(actor.userId(),MissionCode.SAY_SOMETHING);
        boolean can=target.authorId()!=actor.userId()&&"OPEN".equals(participation.event(true).status())&&mission.active()&&!"LOCKED".equals(mission.state())&&participation.eligible(actor.userId());
        return new LikeState(repository.liked(target.id(),actor.userId()),repository.count(target.id()),can);
    }
    public LikeState like(AuthPrincipal actor,long card,boolean desired) {
        return like(actor,card,desired,0);
    }
    public LikeState like(AuthPrincipal actor,long card,boolean desired,long generation) {
        limits.check("heart-user",Long.toString(actor.userId()),60,60);var target=target(card,Kind.CHEER);
        return tx.execute(s->{lock(actor,target);participation.requireGeneration(actor.userId(),generation);writeGuard(actor,MissionCode.SAY_SOMETHING);visible(target);
            if(target.authorId()==actor.userId())throw error(403,"SELF_LIKE","내 응원 카드에는 하트를 남길 수 없어요.");
            if(desired)repository.like(card,actor.userId());else repository.unlike(card,actor.userId());return stateLocked(actor,target);
        });
    }
}
