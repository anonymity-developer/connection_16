package com.tagme.operations;

import com.tagme.auth.*;
import com.tagme.participation.ParticipationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.tagme.operations.PostEventModels.*;

@Service
public class PostEventService {
    public static final String VERSION="post-event-v1-current-cohort";
    private final PostEventRepository repository;private final OperationsRepository operations;
    private final AuthRepository accounts;private final ParticipationRepository participation;private final ObjectMapper json;private final TransactionTemplate tx;
    public PostEventService(PostEventRepository repository,OperationsRepository operations,AuthRepository accounts,ParticipationRepository participation,ObjectMapper json,PlatformTransactionManager manager){
        this.repository=repository;this.operations=operations;this.accounts=accounts;this.participation=participation;this.json=json;tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.setTimeout(25);
    }
    private void admin(AuthPrincipal actor){participation.lockPrincipal(actor);if(!"ADMIN".equals(accounts.byId(actor.userId()).orElseThrow(AuthException::invalid).role()))throw new AuthException(403,"ADMIN_REQUIRED","관리자 권한이 필요해요.");}
    public Report report(AuthPrincipal actor){return tx.execute(s->{admin(actor);var at=repository.now();var facts=operations.awardFacts(at);var ranking=AwardCalculator.rank(facts);
        var candidates=new ArrayList<Candidate>();for(var category:AwardModels.Category.values())for(var row:ranking.categories().get(category))candidates.add(new Candidate(category.name(),row.rank(),"P-"+row.userId(),row.score(),row.validCompletionAt(),row.profileRegisteredAt(),row.metricTie(),row.unresolvedTie(),row.overlappingTopCategories(),row.proofs().stream().map(p->new Evidence(p.mission(),p.sourceKind(),p.sourceId(),p.receiptOrder(),p.acceptedAt(),p.valid())).toList()));
        var reviewKeys=facts.reviews().stream().map(r->r.userId()+":"+r.sourceId()).collect(java.util.stream.Collectors.toSet());
        var reviews=new ArrayList<Review>();facts.reviews().forEach(r->reviews.add(new Review("P-"+r.userId(),r.sourceId(),r.reason())));facts.proofs().stream().filter(p->!p.valid()&&!reviewKeys.contains(p.userId()+":"+p.sourceId())).forEach(p->reviews.add(new Review("P-"+p.userId(),p.sourceId(),"INVALID_"+p.mission())));
        var metrics=new ArrayList<>(repository.metrics(at));metrics.add(new Metric("awardEligibleFive",ranking.eligibleCompleteCount(),"명","기존 시상 계산 그대로: 현재 공개/동의·5종 유효 근거. 일반 참여 통계와 자격 범위가 다를 수 있음"));metrics.add(new Metric("pendingModerationAll",facts.pendingModeration(),"건","전체 운영 큐 미처리 작업. 탈퇴/숨김도 포함하여 누락하지 않음"));metrics.add(new Metric("invalidAwardProofs",ranking.invalidProofs(),"건","기존 시상 유효성 검증에 실패한 완료 근거; 지급 원장 삭제 아님"));
        var hours=repository.hours(at);String phase=phase(facts.window());
        var data=new Data(facts.window(),phase,"현재 ACTIVE·USER 계정 cohort; DB 누적 기록을 조회 시각까지 집계. 행사 구간 필터가 아니며 사전 등록 포함. 공개 가능 활동은 현재 동의/공개 자격으로 별도 제한. 관리자·탈퇴·정지 계정은 일반 통계에서 제외. 시상은 기존 별도 유효성 기준.",List.copyOf(metrics),repository.missions(at),repository.buckets(at),hours.stream().limit(1000).toList(),hours.size()>1000,List.copyOf(candidates),reviews.stream().limit(100).toList(),reviews.size(),
          List.of("중복 수상 허용 및 배분 기준 확정 필요","랜덤 참여상 적격 조건·인원 미확정: 추첨하지 않음","남은 검수의 최종 처리 기준 확정 필요","공식 확정·정정 승인 절차와 결과 발표 시점 확정 필요","개인별 활동 로그 반출 목적·접근 권한·보관 기간 승인 필요"),
          List.of("관찰 지표이며 인간관계 향상·행사 성공을 자동 판단하지 않음","퍼널 전환율·이탈·시각화는 제공하지 않음. 각 단계는 독립 상태 수치","NFC 스캔 횟수·페이지 조회·체류 시간·삭제된 하트 취소 이력은 집계 불가","PROFILE 최초 공개 시각은 현재 필수 조건 완성 시각과 같다고 단정할 수 없음","시간대는 UTC 1시간 버킷. 고유 ID/시간/활동과 원장 건수를 함께 표시; 최대 최근 1000개 버킷","현재 TAP 저장 상태 COUNTDOWN은 시간이 지나도 남을 수 있음. 게임 상태 변경·공식 기회 재지급 안 함","완성/검수 승인 원장과 현재 시상 유효 자격은 별도. 공개 철회에 따라 공개 활동 집계가 달라짐","현재 남아 있는 데이터만 조회하며 삭제/철회 이전의 과거 상태는 복원하지 않음"),false);
        return new Report(VERSION,hash(data),data);
    });}
    static String phase(AwardModels.Window w){return w.opensAt()==null?"NOT_CONFIGURED":w.observedAt().isBefore(w.opensAt())?"BEFORE_OPEN":w.observedAt().isBefore(w.closesAt())?"OPEN":!w.observedAt().isAfter(w.moderationClosesAt())?"MODERATION":"REVIEW";}
    private String hash(Data data){try{
        // Exclude only observation time; retain all rules, deadlines, metrics, sources, flags and scope.
        var tree=json.valueToTree(data);((com.fasterxml.jackson.databind.node.ObjectNode)tree.get("window")).remove("observedAt");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((VERSION+json.writeValueAsString(tree)).getBytes(StandardCharsets.UTF_8)));
    }catch(Exception failure){throw new IllegalStateException("Cannot encode result snapshot",failure);}}
    public History history(AuthPrincipal actor,long id,String purpose,long after){return tx.execute(s->{admin(actor);
        if(!"RESULT_RECONCILIATION".equals(purpose)||id<1||after<0)throw new AuthException(400,"INVALID_FILTER","조회 목적과 내부 참가자 번호를 확인해 주세요.");
        var at=repository.now();if(!repository.participantExists(id,at))throw new AuthException(404,"PARTICIPANT_UNAVAILABLE","조회 가능한 참가자가 아니에요.");
        var facts=operations.awardFacts(at);var rows=repository.receipts(id,after,at);
        return new History("P-"+id,at,purpose,repository.firstProfile(id),repository.steps(id,at,facts.proofs()),rows.stream().limit(50).toList(),rows.size()>50?rows.get(49).cursor():null,"운영상 결과 대조를 위한 제한 조회. 접수 원장·미션 상태만 표시; 감사 로그/원문/외부 ID/개인별 다운로드 없음. 접근/보관 정책 승인 후 용도 확대 검토");
    });}
    public Report forExport(AuthPrincipal actor,String expected){var report=report(actor);if(expected==null||!report.snapshotId().equals(expected))throw new AuthException(409,"RESULT_CHANGED","집계가 바뀌었어요. 화면을 새로 조회한 뒤 내려받아 주세요.");return report;}
}
