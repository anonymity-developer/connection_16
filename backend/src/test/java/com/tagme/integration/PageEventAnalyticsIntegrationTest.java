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
class PageEventAnalyticsIntegrationTest {
    @Autowired com.tagme.analytics.FunnelService funnel;
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));r.add("tagme.analytics.max-user-events",()->3);r.add("tagme.analytics.completion-recording-enabled",()->true);r.add("tagme.auth.enabled",()->true);r.add("tagme.auth.token",()->"test-only-token");r.add("tagme.auth.channel-id",()->"c".repeat(26));r.add("tagme.auth.hmac-key",()->Base64.getEncoder().encodeToString(new byte[32]));}
    @Autowired JdbcTemplate db;@Autowired AuthRepository accounts;@Autowired PasswordEncoder passwords;@Autowired ParticipationRepository participation;@Autowired ResultsService service;@Autowired ObjectMapper json;@Autowired TestRestTemplate http;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @MockitoSpyBean ResultsRepository repository;@MockitoBean MattermostClient mattermost;
    AuthPrincipal actor;Instant time;final Map<String,String> cookies=new HashMap<>();
    static final String PASSWORD="a long test password";
    String definition="""
      {"title":"테스트 전용 질문","axes":[{"id":"social","negative":"I","positive":"E","tie":"NEGATIVE"}],"questions":[{"id":"q1","text":"테스트 질문","options":[{"id":"a","label":"A","scores":{"social":-1}},{"id":"b","label":"B","scores":{"social":1}}]}],"types":[{"code":"I","title":"테스트 I","description":"테스트 설명"},{"code":"E","title":"테스트 E","description":"테스트 설명"}]}
      """;
    @Autowired com.tagme.analytics.PageEventService pages;
    @Autowired com.tagme.participation.ParticipationService participantService;
    @Autowired com.tagme.content.ContentService content;
    @Autowired com.tagme.analytics.AdminPageEvents analytics;
    @Autowired com.tagme.analytics.CompletionEventRecorder recorder;
    @Autowired org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor analyticsExecutor;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @Autowired com.tagme.operations.TapRecoveryService tapRecovery;
    void drained() { try { analyticsExecutor.submit(()->{}).get(10,TimeUnit.SECONDS); } catch(Exception failure) { throw new AssertionError(failure); } }
    @AfterEach void drain() { drained(); }
    @BeforeEach void setup(){
        drained();db.update("DELETE FROM page_events");db.update("UPDATE page_event_collection SET stored_events=0");
        db.update("UPDATE content_items SET approved_revision_id=NULL,pending_revision_id=NULL");
        for(String table:List.of("moderation_reviews","moderation_jobs","content_revisions","content_items"))db.update("DELETE FROM "+table);
        reset(repository,mattermost);cookies.clear();
        db.update("DELETE FROM mission_funnel_events");db.update("DELETE FROM tap_recoveries");
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

    com.tagme.analytics.PageEventsController.Event page(String screen,String parent) {return new com.tagme.analytics.PageEventsController.Event(UUID.randomUUID().toString(),screen,"PAGE_VIEW",null,parent);}
    com.tagme.analytics.PageEventsController.Event step(String screen,String step) {return new com.tagme.analytics.PageEventsController.Event(UUID.randomUUID().toString(),screen,"MISSION_STEP_VIEW",step,null);}
    AuthPrincipal admin(){long id=accounts.insertUser("z".repeat(26),"analytics-admin");accounts.insertCredential(id,passwords.encode(PASSWORD));db.update("UPDATE users SET role='ADMIN' WHERE id=?",id);return accounts.byId(id).orElseThrow().principal();}
    com.tagme.analytics.AdminPageEvents.Window period(){return com.tagme.analytics.AdminPageEvents.window(Instant.now().minusSeconds(86400),Instant.now().plusSeconds(3600));}
    SyncRequest answer(){return new SyncRequest("test-v1",List.of(new Answer("q1","a")));}
    @Test void idempotentConcurrentRetryPayloadConflictAndRealReentry() throws Exception {
        var first=page("HOME",null);String session="a".repeat(64);
        try(var pool=Executors.newFixedThreadPool(4)){var tasks=new ArrayList<Future<?>>();for(int i=0;i<8;i++)tasks.add(pool.submit(()->pages.collect(actor,first,0,session)));for(var task:tasks)task.get(30,TimeUnit.SECONDS);}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->pages.collect(actor,new com.tagme.analytics.PageEventsController.Event(first.eventKey(),"ACCOUNT","PAGE_VIEW",null,null),0,session)).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).status()).isEqualTo(409));
        pages.collect(actor,page("HOME",first.eventKey()),0,session);
        var summary=analytics.summary(admin(),period());
        assertThat(summary.pages()).singleElement().satisfies(row->{assertThat(row.views()).isEqualTo(2);assertThat(row.visitors()).isEqualTo(1);assertThat(row.repeatVisitors()).isEqualTo(1);});
        assertThat(summary.flows()).singleElement().satisfies(edge->assertThat(edge.moves()).isEqualTo(1));
    }
    @Test void flowsNeverJoinDifferentUsersSessionsMissingEventsOrFuturePredecessors() {
        String session="a".repeat(64);var first=page("HOME",null);pages.collect(actor,first,0,session);
        pages.collect(actor,page("ACCOUNT",first.eventKey()),0,"b".repeat(64));
        pages.collect(actor,page("PROFILE_DETAIL",UUID.randomUUID().toString()),0,session);
        long id=accounts.insertUser("b".repeat(26),"bob");accounts.insertCredential(id,accounts.byId(actor.userId()).orElseThrow().hash());
        pages.collect(accounts.byId(id).orElseThrow().principal(),page("ACCOUNT",first.eventKey()),0,session);
        assertThat(analytics.summary(admin(),period()).flows()).isEmpty();
    }
    @Test void unionReadsLegacyQuestionsOnceAndAppliesFiltersAndServerPagination() {
        String session="a".repeat(64);pages.collect(actor,page("SYNC_ME",null),0,session);
        funnel.collect(actor,new com.tagme.analytics.FunnelController.Event(UUID.randomUUID().toString(),"SYNC_ME","QUESTION_VIEW","test-v1",1,Instant.now()),0,session);
        var operator=admin();var log=analytics.log(operator,period(),null,null,0,1);
        assertThat(log.total()).isEqualTo(2);assertThat(log.nextOffset()).isEqualTo(1);
        var questions=analytics.log(operator,period(),"SYNC_ME","MISSION_STEP_VIEW",0,50);
        assertThat(questions.items()).singleElement().satisfies(row->{assertThat(row.step()).isEqualTo("QUESTION_1");assertThat(row.source()).isEqualTo("SYNC");assertThat(row.participantRef()).isEqualTo("P-"+actor.userId());assertThat(row.sessionRef()).hasSize(16);});
        db.update("UPDATE mission_funnel_events SET analytics_session_key=NULL");assertThat(analytics.log(operator,period(),null,"MISSION_STEP_VIEW",0,50).items().getFirst().sessionRef()).isNull();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events WHERE event_type='MISSION_STEP_VIEW'",Integer.class)).isZero();
    }
    @Test void committedSuccessOnlyAfterBusinessCommitAndRetryDoesNotDuplicate() {
        service.submit(actor,answer());service.submit(actor,answer());drained();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events WHERE source='SERVER'",Integer.class)).isEqualTo(1);
        assertThat(analytics.log(admin(),period(),null,"MISSION_SUBMIT_SUCCESS",0,50).items()).singleElement().satisfies(row->{assertThat(row.mission()).isEqualTo("SYNC_ME");assertThat(row.source()).isEqualTo("SERVER");assertThat(row.eventAt()).isBeforeOrEqualTo(row.recordedAt());});
    }
    @Test void allFiveRealMissionPathsRecordOneSuccessEachAndPreserveCollection() {
        assertThat(participantService.reconcileShowMe(actor)).isTrue();
        service.submit(actor,answer());
        db.update("INSERT INTO mission_progress(user_id,mission_id,unlocked_at) SELECT ?,id,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE FROM missions WHERE code IN ('MAKE_A_WISH','SAY_SOMETHING')",actor.userId());
        for(var kind:List.of(com.tagme.content.ContentModels.Kind.WISH,com.tagme.content.ContentModels.Kind.CHEER)) {
            var body=new com.tagme.content.ContentController.Submit("isolated analytics fixture","PLAIN","ROSE",true,null);
            assertThat(content.submit(actor,kind,UUID.randomUUID().toString(),body,false).missionCompleted()).isTrue();
        }
        var attempt=service.start(actor,"analytics-tap-key-0001");time=attempt.endsAt().plusMillis(100);
        assertThat(service.finish(actor,new FinishRequest(attempt.attemptId(),List.of(100,200,500),List.of())).status()).isEqualTo("VALID");
        drained();
        assertThat(participantService.collection(actor.userId()).completion().complete()).isTrue();
        assertThat(db.queryForList("SELECT mission FROM page_events WHERE source='SERVER'",String.class)).containsExactlyInAnyOrder("SHOW_ME","SYNC_ME","MAKE_A_WISH","FAST_TAP","SAY_SOMETHING");
        assertThat(participantService.reconcileShowMe(actor)).isFalse();service.submit(actor,answer());drained();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events",Integer.class)).isEqualTo(5);
    }
    @Test void rollbackManualGrantAndBeforeCollectionNotRecorded() {
        var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        tx.executeWithoutResult(s->{service.submit(actor,answer());s.setRollbackOnly();});drained();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events",Integer.class)).isZero();
        recorder.completed(new MissionCompletedNotice(actor.userId(),"SYNC_ME",999,"ADMIN_ADJUSTMENT",Instant.now()));
        recorder.completed(new MissionCompletedNotice(actor.userId(),"SYNC_ME",998,"SYNC_RESULT",Instant.EPOCH));drained();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events",Integer.class)).isZero();
    }
    @Test void mainTapRecoveryRecordsOneCommittedSuccessWithoutRewritingOriginalTapEvidence() throws Exception {
        var attempt=service.start(actor,"analytics-recovery-key-001");
        var taps=List.of(100,100,230);var payload=new LinkedHashMap<String,Object>();
        payload.put("taps",taps);payload.put("interruptions",List.of());
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var start=db.queryForObject("SELECT UTC_TIMESTAMP(6)-INTERVAL 30 SECOND",java.sql.Timestamp.class).toInstant();
        var submitted=start.plusSeconds(12);
        db.update("UPDATE tap_attempts SET starts_at=?,ends_at=?,submit_deadline=?,status='INVALID',review_reason='DUPLICATE_MILLISECOND',tap_count=3,payload_hash=?,submitted_at=? WHERE id=?",
            java.sql.Timestamp.from(start),java.sql.Timestamp.from(start.plusSeconds(10)),java.sql.Timestamp.from(start.plusSeconds(15)),hash,java.sql.Timestamp.from(submitted),attempt.attemptId());
        for(int at:taps)db.update("INSERT INTO tap_attempt_events(attempt_id,event_type,relative_time_ms) VALUES (?,'TAP',?)",attempt.attemptId(),at);
        assertThat(tapRecovery.restoreAutomatically(actor.userId())).isNotNull();drained();
        assertThat(repository.current(actor.userId()).status()).isEqualTo("VALID");
        assertThat(repository.current(actor.userId()).submittedAt()).isEqualTo(submitted);
        assertThat(repository.events(attempt.attemptId()).taps()).containsExactly(100,100,230);
        assertThat(db.queryForObject("SELECT recognized_at FROM tap_recoveries WHERE attempt_id=?",java.sql.Timestamp.class,attempt.attemptId()).toInstant()).isEqualTo(submitted);
        assertThat(db.queryForObject("SELECT actor_id FROM tap_recoveries WHERE attempt_id=?",Long.class,attempt.attemptId())).isNull();
        assertThat(participation.mission(actor.userId(),ParticipationModels.MissionCode.FAST_TAP).state()).isEqualTo("COMPLETED");
        assertThat(db.queryForList("SELECT mission FROM page_events WHERE source='SERVER'",String.class)).containsExactly("FAST_TAP");
        assertThat(tapRecovery.restoreAutomatically(actor.userId())).isNull();drained();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events WHERE source='SERVER'",Integer.class)).isEqualTo(1);
    }
    @Test void loggingStorageOutageCannotBreakServerResultOrLetter() {
        long dropped=recorder.dropped();db.execute("RENAME TABLE page_events TO isolated_unavailable_page_events");
        try {assertThat(service.submit(actor,answer())).isNotNull();drained();assertThat(repository.sync(actor.userId())).isNotNull();assertThat(participation.mission(actor.userId(),ParticipationModels.MissionCode.SYNC_ME).state()).isEqualTo("COMPLETED");assertThat(recorder.dropped()).isEqualTo(dropped+1);}
        finally{drained();db.execute("RENAME TABLE isolated_unavailable_page_events TO page_events");}
    }
    @Test void rejectsAdminLockedAndStaleStepsInvalidScreensAndMissingStep() {
        String session="a".repeat(64);
        assertThatThrownBy(()->pages.collect(admin(),page("HOME",null),0,session)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->pages.collect(actor,page("UNDEFINED",null),0,session)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->pages.collect(actor,step("SYNC_ME",null),0,session)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->pages.collect(actor,step("FAST_TAP","READY"),9,session)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->pages.collect(actor,step("WISH","COMPOSE"),0,session)).isInstanceOf(AuthException.class);
        pages.collect(actor,page("WISH",null),0,session); // A locked page itself can be displayed.
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events",Integer.class)).isEqualTo(1);
    }
    @Test void everyCatalogScreenMatchesAdditiveDatabaseConstraints() {
        for(var screen:com.tagme.analytics.PageEventCatalog.SCREENS) {
            db.update("DELETE FROM page_events");
            pages.collect(actor,page(screen.id(),null),0,"a".repeat(64));
            assertThat(db.queryForObject("SELECT screen_id FROM page_events",String.class)).isEqualTo(screen.id());
        }
    }
    @Test void periodHalfOpenKstDaysInactiveCohortAndEmptyData() {
        var operator=admin();assertThat(analytics.summary(operator,period()).pages()).isEmpty();
        assertThat(analytics.summary(operator,period()).funnels()).allSatisfy(f->assertThat(f.stages()).allSatisfy(s->{assertThat(s.users()).isZero();assertThat(s.entryConversion()).isNull();}));
        pages.collect(actor,page("HOME",null),0,"a".repeat(64));
        db.update("UPDATE page_events SET recorded_at='2026-11-16 15:00:00.000000'");
        var start=Instant.parse("2026-11-16T15:00:00Z");var w=com.tagme.analytics.AdminPageEvents.window(start,start.plusSeconds(1));
        assertThat(analytics.summary(operator,w).days()).singleElement().satisfies(day->assertThat(day.date()).isEqualTo("2026-11-17"));
        assertThat(analytics.log(operator,com.tagme.analytics.AdminPageEvents.window(start.minusSeconds(1),start),null,null,0,50).total()).isZero();
        db.update("UPDATE users SET status='SUSPENDED' WHERE id=?",actor.userId());assertThat(analytics.log(operator,w,null,null,0,50).total()).isZero();
        assertThatThrownBy(()->com.tagme.analytics.AdminPageEvents.window(start,start.plusSeconds(32*86400))).isInstanceOf(AuthException.class);
    }
    @Test void storageAndRateCapsAreEnforcedUnderUserLock() {
        String session="a".repeat(64);db.update("UPDATE page_event_collection SET stored_events=1000000");
        assertThatThrownBy(()->pages.collect(actor,page("HOME",null),0,session)).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).status()).isEqualTo(429));
        db.update("UPDATE page_event_collection SET stored_events=0");var first=page("HOME",null);
        for(int i=0;i<239;i++)pages.collect(actor,first,0,session);
        assertThatThrownBy(()->pages.collect(actor,first,0,session)).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).status()).isEqualTo(429));
    }
    @Test void perUserLifetimeCapDoesNotCountIdempotentReplayAsNewStorage() {
        var first=page("HOME",null);String session="a".repeat(64);
        pages.collect(actor,first,0,session);pages.collect(actor,first,0,session);
        pages.collect(actor,page("ACCOUNT",null),0,session);pages.collect(actor,page("HOME",null),0,session);
        assertThatThrownBy(()->pages.collect(actor,page("ACCOUNT",null),0,session)).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).status()).isEqualTo(429));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events",Integer.class)).isEqualTo(3);
    }
    @Test void globalStorageReservationIsAtomicAcrossConcurrentRequests() throws Exception {
        db.update("UPDATE page_event_collection SET stored_events=999999");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<Future<Boolean>>();
            for(int i=0;i<2;i++)tasks.add(pool.submit(()->{try{pages.collect(actor,page("HOME",null),0,"a".repeat(64));return true;}catch(AuthException cap){assertThat(cap.status()).isEqualTo(429);return false;}}));
            int accepted=0;for(var task:tasks)if(task.get(30,TimeUnit.SECONDS))accepted++;
            assertThat(accepted).isEqualTo(1);assertThat(db.queryForObject("SELECT stored_events FROM page_event_collection WHERE id=1",Long.class)).isEqualTo(1000000);
        }
    }
    @Test void httpAuthCsrfPrivacyValidationAndSessionPseudonym() {
        assertThat(raw(HttpMethod.GET,"/admin/events",null,null).getStatusCode().value()).isEqualTo(401);
        assertThat(send("/analytics/events",page("HOME",null)).getStatusCode().value()).isEqualTo(401);
        assertThat(send("/auth/login",Map.of("username","alice","password",PASSWORD)).getStatusCode().value()).isEqualTo(200);
        assertThat(raw(HttpMethod.GET,"/admin/events/summary",null,null).getStatusCode().value()).isEqualTo(403);
        assertThat(raw(HttpMethod.POST,"/analytics/events",page("HOME",null),null).getStatusCode().value()).isEqualTo(403);
        assertThat(send("/analytics/events",Map.of("eventKey",UUID.randomUUID().toString(),"screen","HOME","type","MISSION_SUBMIT_SUCCESS")).getStatusCode().value()).isEqualTo(400);
        assertThat(send("/analytics/events",Map.of("eventKey",UUID.randomUUID().toString(),"screen","HOME","type","PAGE_VIEW","answer","never-store")).getStatusCode().value()).isEqualTo(400);
        assertThat(send("/analytics/events",page("HOME",null)).getStatusCode().value()).isEqualTo(204);
        String digest=db.queryForObject("SELECT session_key FROM page_events",String.class);
        assertThat(digest).matches("[a-f0-9]{64}");assertThat(cookies.values()).doesNotContain(digest);
        db.update("UPDATE users SET role='ADMIN' WHERE id=?",actor.userId());cookies.clear();
        assertThat(send("/auth/login",Map.of("username","alice","password",PASSWORD)).getStatusCode().value()).isEqualTo(200);
        assertThat(raw(HttpMethod.GET,"/admin/events",null,null).getStatusCode().value()).isEqualTo(200);
        assertThat(send("/analytics/events",page("HOME",null)).getStatusCode().value()).isEqualTo(403);
    }
    ResponseEntity<Map> raw(HttpMethod method,String path,Object body,String csrf){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(!cookies.isEmpty())h.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));if(csrf!=null)h.add("X-XSRF-TOKEN",csrf);var r=http.exchange("/api/v1"+path,method,new HttpEntity<>(body,h),Map.class);for(String c:r.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)){var p=c.split(";",2)[0].split("=",2);if(p.length==2)cookies.put(p[0],p[1]);}return r;}
    ResponseEntity<Map> send(String path,Object body){var c=raw(HttpMethod.GET,"/csrf",null,null);return raw(HttpMethod.POST,path,body,(String)c.getBody().get("token"));}
}
