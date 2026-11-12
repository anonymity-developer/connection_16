package com.tagme.integration;

import com.tagme.auth.*;
import com.tagme.participation.*;
import com.tagme.results.*;
import com.tagme.results.ResultsController.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration") @Testcontainers @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResultsIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));r.add("tagme.auth.enabled",()->true);r.add("tagme.auth.token",()->"test-only-token");r.add("tagme.auth.channel-id",()->"c".repeat(26));r.add("tagme.auth.hmac-key",()->Base64.getEncoder().encodeToString(new byte[32]));}
    @Autowired JdbcTemplate db;@Autowired AuthRepository accounts;@Autowired PasswordEncoder passwords;@Autowired ParticipationRepository participation;@Autowired ResultsService service;@Autowired ObjectMapper json;@Autowired TestRestTemplate http;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @MockitoSpyBean ResultsRepository repository;@MockitoBean MattermostClient mattermost;
    AuthPrincipal actor;Instant time;final Map<String,String> cookies=new HashMap<>();
    static final String PASSWORD="a long test password";
    String definition="""
      {"title":"테스트 전용 질문","axes":[{"id":"social","negative":"I","positive":"E","tie":"NEGATIVE"}],"questions":[{"id":"q1","text":"테스트 질문","options":[{"id":"a","label":"A","scores":{"social":-1}},{"id":"b","label":"B","scores":{"social":1}}]}],"types":[{"code":"I","title":"테스트 I","description":"테스트 설명"},{"code":"E","title":"테스트 E","description":"테스트 설명"}]}
      """;
    @BeforeEach void setup(){
        reset(repository,mattermost);cookies.clear();
        for(String table:List.of("mission_completion_evidence","submission_receipts","event_completions","mission_progress","tap_attempt_events","tap_slots","tap_attempts","sync_results","current_sync_questionnaire","sync_questionnaires","tags","user_consent_events","user_consents","user_interests","profiles","user_credentials","users","current_consent_policies","consent_policies","event_settings"))db.update("DELETE FROM "+table);
        try(var c=redis.getConnectionFactory().getConnection()){c.serverCommands().flushDb();}
        db.update("UPDATE event_completion_sequence SET next_value=1");db.update("UPDATE missions SET active=TRUE");
        db.update("INSERT INTO event_settings(id,opens_at,submit_closes_at,moderation_closes_at) VALUES (1,UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 2 HOUR)");
        long id=accounts.insertUser("a".repeat(26),"alice");accounts.insertCredential(id,passwords.encode(PASSWORD));actor=accounts.byId(id).orElseThrow().principal();
        db.update("UPDATE users SET mattermost_profile_synced_at=UTC_TIMESTAMP(6),real_name='테스트',region='서울',class_no=1 WHERE id=?",id);db.update("INSERT INTO profiles(user_id,nickname,bio,is_public) VALUES (?,'테스트','테스트 소개',TRUE)",id);db.update("INSERT INTO user_interests SELECT ?,MIN(id) FROM interests WHERE active=TRUE GROUP BY category",id);
        for(String type:List.of("PARTICIPATION","PROFILE_PUBLICATION")){db.update("INSERT INTO consent_policies VALUES (?,'test-v1','테스트','테스트 전용')",type);db.update("INSERT INTO current_consent_policies VALUES (?,'test-v1')",type);db.update("INSERT INTO user_consents(user_id,policy_type,policy_version,agreed_at) VALUES (?,?,'test-v1',UTC_TIMESTAMP(6))",id,type);}
        db.update("INSERT INTO mission_progress(user_id,mission_id,unlocked_at) SELECT ?,id,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE FROM missions WHERE code IN ('SYNC_ME','FAST_TAP')",id);
        db.update("INSERT INTO sync_questionnaires VALUES ('test-v1',?)",definition);db.update("INSERT INTO current_sync_questionnaire VALUES (1,'test-v1')");
        time=repository.now();doAnswer(i->time).when(repository).now();
        when(mattermost.byUsername("alice")).thenReturn(Optional.of(new MattermostClient.Identity("a".repeat(26),"alice",new MattermostProfile("테스트이름","서울",13,0))));
    }
    SyncRequest answer(String option){return new SyncRequest("test-v1",List.of(new Answer("q1",option)));}
    @Test void syncComputesFirstResultAndCannotChangeOrRepeatCompletion(){var result=service.submit(actor,answer("b"));assertThat(result.type().code()).isEqualTo("E");assertThat(result.axisScores()).containsEntry("social",1);assertThat(service.submit(actor,answer("b"))).isEqualTo(result);assertThatThrownBy(()->service.submit(actor,answer("a"))).isInstanceOf(AuthException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM sync_results",Integer.class)).isEqualTo(1);assertThat(participation.mission(actor.userId(),com.tagme.participation.ParticipationModels.MissionCode.SYNC_ME).state()).isEqualTo("COMPLETED");}
    @Test void syncMissingConfigUnknownOptionAndConcurrentFirstSubmissionsAreSafe()throws Exception{
        db.update("DELETE FROM current_sync_questionnaire");assertThat(service.questions(actor).available()).isFalse();assertThatThrownBy(()->service.submit(actor,answer("a"))).isInstanceOf(AuthException.class);
        db.update("INSERT INTO current_sync_questionnaire VALUES (1,'test-v1')");assertThatThrownBy(()->service.submit(actor,answer("x"))).isInstanceOf(AuthException.class);
        try(var pool=Executors.newFixedThreadPool(8)){List<Future<Boolean>> futures=new ArrayList<>();for(int i=0;i<16;i++){String option=i%2==0?"a":"b";futures.add(pool.submit(()->{try{service.submit(actor,answer(option));return true;}catch(AuthException conflict){assertThat(conflict.code()).isEqualTo("SYNC_ALREADY_SUBMITTED");return false;}}));}int success=0;for(var f:futures)if(f.get(30,TimeUnit.SECONDS))success++;assertThat(success).isEqualTo(8);}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);
    }
    @Test void tapStartRetryCancelBeforeStartAndInterruptionConsumeOnlyTheRightSlot(){var first=service.start(actor,"start-key-first-0001");assertThat(service.start(actor,"start-key-first-0001").attemptId()).isEqualTo(first.attemptId());assertThatThrownBy(()->service.start(actor,"start-key-other-0001")).isInstanceOf(AuthException.class);assertThat(service.cancel(actor,first.attemptId()).status()).isEqualTo("CANCELLED");assertThat(service.tap(actor.userId()).officialAvailable()).isTrue();var second=service.start(actor,"start-key-second-001");assertThat(second.generation()).isEqualTo(2);time=second.startsAt().plusSeconds(1);assertThat(service.cancel(actor,second.attemptId()).status()).isEqualTo("INTERRUPTED");assertThat(service.tap(actor.userId()).officialAvailable()).isFalse();assertThatThrownBy(()->service.start(actor,"start-key-third-0001")).isInstanceOf(AuthException.class);}
    @Test void validTapDerivesScoreAndFinishRetryKeepsEvidenceExactlyOnce()throws Exception{var attempt=service.start(actor,"valid-start-key-001");var body=new FinishRequest(attempt.attemptId(),List.of(100,200,500),List.of());assertThatThrownBy(()->service.finish(actor,body)).isInstanceOf(AuthException.class);time=attempt.endsAt().plusMillis(100);try(var pool=Executors.newFixedThreadPool(4)){List<Future<TapAttempt>> futures=new ArrayList<>();for(int i=0;i<12;i++)futures.add(pool.submit(()->service.finish(actor,body)));for(var f:futures)assertThat(f.get(30,TimeUnit.SECONDS).tapCount()).isEqualTo(3);}assertThat(service.tap(actor.userId()).attempt().status()).isEqualTo("VALID");assertThatThrownBy(()->service.finish(actor,new FinishRequest(attempt.attemptId(),List.of(100,200),List.of()))).isInstanceOf(AuthException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_attempt_events",Integer.class)).isEqualTo(3);assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);}
    @Test void rapidTapAtOneMillisecondIsStoredCompletedAndRestored() {
        var attempt=service.start(actor,"rapid-one-ms-key-001");time=attempt.endsAt().plusMillis(100);
        var taps=java.util.stream.IntStream.range(0,300).boxed().toList();
        var result=service.finish(actor,new FinishRequest(attempt.attemptId(),taps,List.of()));
        assertThat(result.status()).isEqualTo("VALID");assertThat(result.tapCount()).isEqualTo(300);
        assertThat(service.tap(actor.userId()).attempt().tapCount()).isEqualTo(300);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_attempt_events",Integer.class)).isEqualTo(300);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);
    }
    @Test void duplicateMillisecondIsAutomaticallyInvalidAndNeverCompletes(){var attempt=service.start(actor,"suspicious-key-001");time=attempt.endsAt().plusMillis(100);assertThat(service.finish(actor,new FinishRequest(attempt.attemptId(),List.of(1,1,3),List.of())).status()).isEqualTo("INVALID");assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isZero();}
    @Test void expiredSlotIsNotReissuedAndLateSubmissionNeverGetsAValidScore(){var attempt=service.start(actor,"expired-start-key-01");time=attempt.submitDeadline().plusMillis(1);assertThat(service.tap(actor.userId()).attempt().status()).isEqualTo("EXPIRED");assertThatThrownBy(()->service.start(actor,"another-start-key-01")).isInstanceOf(AuthException.class);assertThat(service.finish(actor,new FinishRequest(attempt.attemptId(),List.of(100),List.of())).status()).isEqualTo("EXPIRED");assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isZero();}
    @Test void concurrentStartWithTheSameKeyOnlyCreatesOneOfficialAttempt() throws Exception {
        try(var pool=Executors.newFixedThreadPool(6)) {
            List<Future<TapAttempt>> futures=new ArrayList<>();for(int i=0;i<16;i++)futures.add(pool.submit(()->service.start(actor,"concurrent-start-key-01")));
            Set<Long> ids=new HashSet<>();for(var result:futures)ids.add(result.get(30,TimeUnit.SECONDS).attemptId());assertThat(ids).hasSize(1);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_attempts",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_slots",Integer.class)).isEqualTo(1);
    }
    @Test void interruptionAndFailedFinishNeverGrantACompletionUntilARealCommit() {
        var attempt=service.start(actor,"finish-rollback-key-01");time=attempt.endsAt().plusMillis(100);
        var payload=new FinishRequest(attempt.attemptId(),List.of(100,200),List.of());
        doAnswer(invocation->{invocation.callRealMethod();throw new org.springframework.dao.DataAccessResourceFailureException("test rollback after events");}).when(repository).finish(any(),any(),anyString(),anyList(),anyList(),any());
        assertThatThrownBy(()->service.finish(actor,payload)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(repository.current(actor.userId()).status()).isEqualTo("COUNTDOWN");assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_attempt_events",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isZero();
        doCallRealMethod().when(repository).finish(any(),any(),anyString(),anyList(),anyList(),any());
        assertThat(service.finish(actor,new FinishRequest(attempt.attemptId(),List.of(100),List.of(new Interruption("HIDDEN",300)))).status()).isEqualTo("INTERRUPTED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isZero();
    }
    @Test void replacingQuestionnaireVersionNeverRecomputesAStoredResult() {
        var first=service.submit(actor,answer("a"));db.update("INSERT INTO sync_questionnaires VALUES ('test-v2',?)",definition);db.update("UPDATE current_sync_questionnaire SET version='test-v2'");
        assertThat(service.sync(actor.userId())).isEqualTo(first);assertThat(service.submit(actor,answer("a"))).isEqualTo(first);
        assertThatThrownBy(()->service.submit(actor,new SyncRequest("test-v2",List.of(new Answer("q1","b"))))).isInstanceOf(AuthException.class);
    }
    @Test void notEnoughTimeLockedMissionAndOtherUsersCannotTakeAnAttempt(){db.update("UPDATE event_settings SET game_duration=20");assertThatThrownBy(()->service.start(actor,"invalid-duration-key-01")).isInstanceOf(AuthException.class).satisfies(error->assertThat(((AuthException)error).code()).isEqualTo("GAME_CONFIG_INVALID"));db.update("UPDATE event_settings SET game_duration=10");db.update("UPDATE event_settings SET submit_closes_at=UTC_TIMESTAMP(6)+INTERVAL 10 SECOND");assertThatThrownBy(()->service.start(actor,"closing-start-key-01")).isInstanceOf(AuthException.class);db.update("UPDATE event_settings SET submit_closes_at=UTC_TIMESTAMP(6)+INTERVAL 1 HOUR");db.update("DELETE FROM mission_progress WHERE mission_id=4");assertThatThrownBy(()->service.start(actor,"locked-start-key-001")).isInstanceOf(AuthException.class);assertThatThrownBy(()->service.cancel(actor,999999)).isInstanceOf(AuthException.class);assertThat(repository.current(actor.userId())).isNull();}
    @Test void anotherUsersAttemptIdCannotBeCancelledOrFinished() {
        var attempt=service.start(actor,"owned-attempt-key-01");long bob=accounts.insertUser("b".repeat(26),"bob");accounts.insertCredential(bob,accounts.byId(actor.userId()).orElseThrow().hash());var other=accounts.byId(bob).orElseThrow().principal();
        assertThatThrownBy(()->service.cancel(other,attempt.attemptId())).isInstanceOf(AuthException.class).satisfies(error->assertThat(((AuthException)error).status()).isEqualTo(404));
        time=attempt.endsAt().plusMillis(100);assertThatThrownBy(()->service.finish(other,new FinishRequest(attempt.attemptId(),List.of(100),List.of()))).isInstanceOf(AuthException.class).satisfies(error->assertThat(((AuthException)error).status()).isEqualTo(404));
        assertThat(repository.current(actor.userId()).status()).isEqualTo("COUNTDOWN");
    }
    @Test void resultAndMissionRollbackTogether(){doAnswer(invocation->{invocation.callRealMethod();throw new org.springframework.dao.DataAccessResourceFailureException("test rollback after insert");}).when(repository).saveSync(anyLong(),anyString(),anyString(),anyString(),anyString(),anyString(),any());assertThatThrownBy(()->service.submit(actor,answer("a"))).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM sync_results",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isZero();}
    ResponseEntity<Map> raw(HttpMethod method,String path,Object body,String csrf){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(!cookies.isEmpty())h.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));if(csrf!=null)h.add("X-XSRF-TOKEN",csrf);var r=http.exchange("/api/v1"+path,method,new HttpEntity<>(body,h),Map.class);for(String c:r.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)){var p=c.split(";",2)[0].split("=",2);if(p.length==2)cookies.put(p[0],p[1]);}return r;}
    ResponseEntity<Map> send(String path,Object body){var c=raw(HttpMethod.GET,"/csrf",null,null);return raw(HttpMethod.POST,path,body,(String)c.getBody().get("token"));}
    @Test void httpCsrfAndExtraScoreOrTypeFieldsNeverBypassServerRules(){assertThat(send("/sync/submit",Map.of("questionnaireVersion","test-v1","answers",List.of(Map.of("questionId","q1","optionId","a")))).getStatusCode().value()).isEqualTo(401);assertThat(send("/auth/login",Map.of("username","alice","password",PASSWORD)).getStatusCode().value()).isEqualTo(200);assertThat(raw(HttpMethod.POST,"/sync/submit",Map.of(),null).getStatusCode().value()).isEqualTo(403);assertThat(send("/sync/submit",Map.of("questionnaireVersion","test-v1","answers",List.of(Map.of("questionId","q1","optionId","a")),"typeCode","E")).getStatusCode().value()).isEqualTo(400);assertThat(send("/tap/finish",Map.of("attemptId",1,"taps",List.of(),"interruptions",List.of(),"tapCount",999)).getStatusCode().value()).isEqualTo(400);}
}
