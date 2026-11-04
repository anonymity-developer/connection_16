package com.tagme.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.auth.*;
import com.tagme.participation.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.tagme.content.ContentModels.*;

@Service
public class ContentService {
    private final ContentRepository repository;
    private final ParticipationRepository participation;
    private final TransactionTemplate tx;
    private final AuthRateLimiter limits;
    private final ObjectMapper json;
    private final SecureRandom random=new SecureRandom();
    public ContentService(ContentRepository repository,ParticipationRepository participation,PlatformTransactionManager manager,AuthRateLimiter limits,ObjectMapper json) {
        this.repository=repository;this.participation=participation;this.tx=new TransactionTemplate(manager);this.limits=limits;this.json=json;
    }
    static AuthException error(int status,String code,String text) { return new AuthException(status,code,text); }
    private void guard(AuthPrincipal actor,Kind kind) {
        participation.lockPrincipal(actor);participation.requireOpen(false);
        var mission=participation.mission(actor.userId(),kind.mission());participation.activeMission(mission.id());
        if("LOCKED".equals(mission.state()))throw error(403,"MISSION_LOCKED","먼저 현장 태그로 미션을 열어주세요.");
        if(!participation.eligible(actor.userId()))throw error(409,"PROFILE_REQUIRED","프로필과 현재 공개 동의를 확인해 주세요.");
    }
    public OwnSlot own(long user,Kind kind) { return tx.execute(s->snapshot(user,kind)); }
    private OwnSlot snapshot(long user,Kind kind) {
        var item=repository.own(user,kind);boolean complete="COMPLETED".equals(participation.mission(user,kind.mission()).state());
        if(item==null)return new OwnSlot(null,0,null,null,null,null,complete,null,null);
        return new OwnSlot(item.id(),item.version(),item.visibility(),item.approvedId()==null?null:repository.revision(item.approvedId()),item.pendingId()==null?null:repository.revision(item.pendingId()),repository.latest(item.id()),complete,item.approvedId()==null?null:item.luckScore(),item.approvedId()==null?null:item.landingZone());
    }
    private record Payload(String content,String templateId,String style,boolean anonymous) { }
    private String hash(ContentController.Submit body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(new Payload(body.content().strip(),body.templateId(),body.style(),body.anonymous())).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception impossible) { throw new IllegalStateException("Cannot encode content",impossible); }
    }
    public OwnSlot submit(AuthPrincipal actor,Kind kind,String key,ContentController.Submit body,boolean edit) {
        if(key==null||!key.matches("[A-Za-z0-9_-]{16,64}"))throw error(400,"INVALID_IDEMPOTENCY_KEY","요청 식별자를 확인해 주세요.");
        if(body.content()==null||body.content().isBlank()||body.content().length()>200||!List.of("PLAIN","NOTE").contains(body.templateId())||!List.of("ROSE","INK").contains(body.style()))throw error(400,"INVALID_CONTENT","문구와 카드 스타일을 확인해 주세요.");
        if(kind==Kind.WISH&&(!body.anonymous()||!"PLAIN".equals(body.templateId())||!"ROSE".equals(body.style())))throw error(400,"INVALID_WISH_STYLE","소원은 익명 기본 형태로 저장돼요.");
        limits.check("content-user",Long.toString(actor.userId()),30,60);Instant received=repository.now();String hash=hash(body);
        return tx.execute(s->{
            participation.lockPrincipal(actor);var item=repository.own(actor.userId(),kind);
            if(item!=null) {
                var replay=repository.byKey(item.id(),key);
                if(replay!=null){if(!replay.hash().equals(hash))throw error(409,"IDEMPOTENCY_CONFLICT","같은 요청의 내용이 변경됐어요. 저장된 글을 먼저 확인해 주세요.");return snapshot(actor.userId(),kind);}
            }
            guard(actor,kind);
            if(!repository.now().isBefore(participation.event(true).submitClosesAt())||received.isBefore(participation.event(true).opensAt()))throw error(403,"EVENT_CLOSED","지금은 제출 가능한 시간이 아니에요.");
            if(item==null) {
                if(edit||body.expectedVersion()!=null)throw error(409,"SLOT_NOT_CREATED","내 글을 다시 불러와 주세요.");
                Integer luck=null,zone=null;
                if(kind==Kind.WISH) { var policy=repository.luckPolicy();if(policy!=null){luck=policy[0]+random.nextInt(policy[1]-policy[0]+1);zone=random.nextInt(policy[2]);} }
                item=repository.item(repository.create(actor.userId(),kind,luck,zone),true);
            } else {
                if(!edit)throw error(409,"SLOT_ALREADY_EXISTS","작성한 글이 있어요. 내 글을 확인해 주세요.");
                if(!"VISIBLE".equals(item.visibility()))throw error(409,"CONTENT_HIDDEN","운영자가 숨긴 글은 수정할 수 없어요.");
                if(body.expectedVersion()==null||body.expectedVersion()!=item.version())throw error(409,"CONTENT_CHANGED","글 상태가 변경됐어요. 다시 불러와 주세요.");
            }
            var latest=repository.latest(item.id());int number=latest==null?1:latest.number()+1;
            if(number>20)throw error(409,"REVISION_LIMIT","수정 횟수를 초과했어요. 운영자에게 문의해 주세요.");
            long revision=repository.addRevision(item.id(),number,body,key,hash,received);repository.queue(item,revision);
            participation.receipt(actor.userId(),kind.source(),revision,received);
            dbProgress(actor.userId(),kind);return snapshot(actor.userId(),kind);
        });
    }
    private void dbProgress(long user,Kind kind) { repository.markInProgress(user,kind.mission().name()); }
    public Feed feed(Kind kind,Long cursor) { if(cursor!=null&&cursor<1)throw error(400,"INVALID_CURSOR","목록 위치를 확인해 주세요.");return repository.feed(kind,cursor==null?Long.MAX_VALUE:cursor); }
}
