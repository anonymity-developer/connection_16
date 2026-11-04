package com.tagme.results;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.auth.*;
import com.tagme.participation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.tagme.participation.ParticipationModels.MissionCode;
import static com.tagme.results.ResultsController.*;

@Service
public class ResultsService {
    private final ParticipationRepository participation;private final ResultsRepository repository;private final MissionCompletionService completion;
    private final TransactionTemplate transactions;private final ObjectMapper json;private final AuthRateLimiter limits;
    public ResultsService(ParticipationRepository participation,ResultsRepository repository,MissionCompletionService completion,PlatformTransactionManager manager,ObjectMapper json,AuthRateLimiter limits){this.participation=participation;this.repository=repository;this.completion=completion;this.transactions=new TransactionTemplate(manager);this.json=json;this.limits=limits;}
    static AuthException error(int status,String code,String message){return new AuthException(status,code,message);}
    private void guard(AuthPrincipal actor,MissionCode code){
        participation.lockPrincipal(actor);participation.requireOpen(false);var mission=participation.mission(actor.userId(),code);participation.activeMission(mission.id());
        if("LOCKED".equals(mission.state()))throw error(403,"MISSION_LOCKED","먼저 현장 태그로 미션을 열어주세요.");
        if(!participation.eligible(actor.userId()))throw error(409,"PROFILE_REQUIRED","프로필과 현재 공개 동의를 확인해 주세요.");
    }
    private Questionnaire parse(ResultsRepository.Definition definition){
        if(definition==null)throw error(503,"QUESTIONNAIRE_NOT_READY","성향 질문을 준비하고 있어요.");
        try{return Questionnaire.parse(definition.json(),json);}catch(IllegalArgumentException invalid){throw error(503,"QUESTIONNAIRE_NOT_READY","성향 질문을 준비하고 있어요.");}
    }
    public Questions questions(AuthPrincipal actor){return transactions.execute(status->{guard(actor,MissionCode.SYNC_ME);var definition=repository.currentDefinition();if(definition==null)return new Questions(false,null,null,List.of());var q=parse(definition);return new Questions(true,definition.version(),q.title(),q.questions().stream().map(question->new Question(question.id(),question.text(),question.options().stream().map(o->new QuestionOption(o.id(),o.label())).toList())).toList());});}
    public List<Questionnaire.Type> types(AuthPrincipal actor){return transactions.execute(status->{guard(actor,MissionCode.SYNC_ME);return parse(repository.currentDefinition()).types();});}
    public SyncResult sync(long userId){var row=repository.sync(userId);return row==null?null:syncResult(row);}
    private SyncResult syncResult(ResultsRepository.SyncRow row){
        try{var q=parse(repository.definition(row.version()));return new SyncResult(row.version(),q.types().stream().filter(t->t.code().equals(row.typeCode())).findFirst().orElseThrow(),json.readValue(row.scores(),new TypeReference<Map<String,Integer>>(){}),row.submittedAt());}
        catch(AuthException failure){throw failure;}catch(Exception invalid){throw error(503,"RESULT_UNAVAILABLE","저장된 결과를 확인하지 못했어요.");}
    }
    public SyncResult submit(AuthPrincipal actor,SyncRequest body){
        limits.check("sync-user",Long.toString(actor.userId()),30,60);Instant received=repository.now();
        return transactions.execute(status->{
            participation.lockPrincipal(actor);
            var existing=repository.sync(actor.userId());
            var definition=existing==null?repository.currentDefinition():repository.definition(existing.version());var q=parse(definition);
            if(!definition.version().equals(body.questionnaireVersion()))throw error(409,"QUESTIONNAIRE_CHANGED","질문 버전이 변경됐어요. 다시 불러와 주세요.");
            Questionnaire.Score score;try{score=q.score(body.answers().stream().map(a->new Questionnaire.Answer(a.questionId(),a.optionId())).toList());}catch(IllegalArgumentException invalid){throw error(400,"INVALID_ANSWERS","모든 질문의 선택지를 확인해 주세요.");}
            String answers=encode(score.canonicalAnswers()),hash=digest(body.questionnaireVersion()+"|"+answers);
            if(existing!=null){if(!existing.hash().equals(hash))throw error(409,"SYNC_ALREADY_SUBMITTED","최초 성향 결과는 바꿀 수 없어요.");return syncResult(existing);}
            guard(actor,MissionCode.SYNC_ME);repository.saveSync(actor.userId(),definition.version(),answers,encode(score.axes()),score.type().code(),hash,received);
            var receipt=participation.receipt(actor.userId(),"SYNC_RESULT",actor.userId(),received);completion.complete(actor.userId(),MissionCode.SYNC_ME,receipt.id());return sync(actor.userId());
        });
    }
    String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception invalid){throw new IllegalStateException("Cannot encode result",invalid);}}
    static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    public TapState tap(long userId){var current=repository.current(userId);return new TapState(current==null,current==null?null:view(current,repository.now()));}
    private TapAttempt view(ResultsRepository.Attempt a,Instant now){
        String state=a.status();if("COUNTDOWN".equals(state))state=now.isAfter(a.deadline())?"EXPIRED":!now.isBefore(a.endsAt())?"AWAITING_SUBMISSION":!now.isBefore(a.startsAt())?"PLAYING":"COUNTDOWN";
        return new TapAttempt(a.id(),a.generation(),state,now,a.startsAt(),a.endsAt(),a.deadline(),a.count(),a.reason(),"VALID".equals(state));
    }
    public TapAttempt start(AuthPrincipal actor,String key){
        if(key==null||!key.matches("[A-Za-z0-9_-]{16,64}"))throw error(400,"INVALID_IDEMPOTENCY_KEY","요청 식별자를 다시 확인해 주세요.");
        limits.check("tap-start-user",Long.toString(actor.userId()),30,60);
        return transactions.execute(status->{
            guard(actor,MissionCode.FAST_TAP);Instant now=repository.now();var replay=repository.byKey(actor.userId(),key);if(replay!=null)return view(replay,now);
            if(repository.gameDuration()!=10)throw error(503,"GAME_CONFIG_INVALID","공식 도전 설정을 확인하고 있어요.");
            if(repository.current(actor.userId())!=null)throw error(409,"TAP_SLOT_USED","공식 도전은 이미 시작했어요. 저장된 상태를 확인해 주세요.");
            Instant starts=now.plusSeconds(5),ends=starts.plusSeconds(10),deadline=ends.plusSeconds(5);
            if(deadline.isAfter(repository.eventClose()))throw error(403,"INSUFFICIENT_EVENT_TIME","행사 마감까지 도전을 끝낼 시간이 부족해요.");
            long id=repository.createAttempt(actor.userId(),key,starts,ends,deadline);return view(repository.attempt(actor.userId(),id),now);
        });
    }
    private ResultsRepository.Attempt owned(long userId,long id){var value=repository.attempt(userId,id);if(value==null)throw error(404,"ATTEMPT_NOT_FOUND","도전 기록을 찾을 수 없어요.");return value;}
    public TapAttempt cancel(AuthPrincipal actor,long id){return transactions.execute(status->{
        participation.lockPrincipal(actor);var attempt=owned(actor.userId(),id);Instant now=repository.now();
        if(!"COUNTDOWN".equals(attempt.status()))return view(attempt,now);
        repository.cancel(attempt,now.isBefore(attempt.startsAt())?"CANCELLED":"INTERRUPTED");return view(owned(actor.userId(),id),now);
    });}
    private record TapPayload(List<Integer> taps,List<TapValidation.Interruption> interruptions) { }
    public TapAttempt finish(AuthPrincipal actor,FinishRequest body){
        limits.check("tap-finish-user",Long.toString(actor.userId()),30,60);Instant received=repository.now();
        final TapValidation.Verdict inspected;var interruptions=body.interruptions().stream().map(e->new TapValidation.Interruption(e.type(),e.atMs())).toList();
        try{inspected=TapValidation.inspect(body.taps(),interruptions,10000);}catch(IllegalArgumentException invalid){throw error(400,"INVALID_TAP_EVENTS","도전 이벤트 형식을 확인해 주세요.");}
        String hash=digest(encode(new TapPayload(body.taps(),interruptions)));
        return transactions.execute(status->{
            participation.lockPrincipal(actor);var attempt=owned(actor.userId(),body.attemptId());Instant now=repository.now();
            if(attempt.hash()!=null){if(!hash.equals(attempt.hash()))throw error(409,"TAP_PAYLOAD_CHANGED","처음 제출한 기록과 다른 요청이에요.");return view(attempt,now);}
            if(!"COUNTDOWN".equals(attempt.status()))throw error(409,"TAP_ALREADY_CLOSED","이미 종료한 도전이에요.");
            if(received.isBefore(attempt.endsAt()))throw error(409,"TAP_TOO_EARLY","도전이 끝난 뒤 결과를 제출해 주세요.");
            guard(actor,MissionCode.FAST_TAP);
            var verdict=received.isAfter(attempt.deadline())?new TapValidation.Verdict("EXPIRED","SUBMISSION_EXPIRED",body.taps().size()):inspected;
            repository.finish(attempt,verdict,hash,body.taps(),interruptions,received);
            if("VALID".equals(verdict.status())){var receipt=participation.receipt(actor.userId(),"GAME_ATTEMPT",attempt.id(),received);completion.complete(actor.userId(),MissionCode.FAST_TAP,receipt.id());}
            return view(owned(actor.userId(),attempt.id()),now);
        });
    }
}
