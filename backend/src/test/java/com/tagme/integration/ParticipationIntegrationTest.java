package com.tagme.integration;

import com.tagme.auth.*;
import com.tagme.participation.*;
import com.tagme.participation.ParticipationModels.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration") @Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ParticipationIntegrationTest.FutureRules.class)
class ParticipationIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl); r.add("spring.datasource.username",MYSQL::getUsername); r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.data.redis.host",REDIS::getHost); r.add("spring.data.redis.port",() -> REDIS.getMappedPort(6379));
        r.add("tagme.auth.enabled",() -> true); r.add("tagme.auth.token",() -> "test-only-token"); r.add("tagme.auth.channel-id",() -> "c".repeat(26));
        r.add("tagme.auth.hmac-key",() -> Base64.getEncoder().encodeToString(new byte[32]));
    }
    // Only tests register the four future mission validators. Production has SHOW_ME only.
    @TestConfiguration static class FutureRules {
        @Bean CompletionRule syncRule() { return fixtureRule(MissionCode.SYNC_ME); }
        @Bean CompletionRule wishRule() { return fixtureRule(MissionCode.MAKE_A_WISH); }
        @Bean CompletionRule tapRule() { return fixtureRule(MissionCode.FAST_TAP); }
        @Bean CompletionRule cheerRule() { return fixtureRule(MissionCode.SAY_SOMETHING); }
        static CompletionRule fixtureRule(MissionCode code) {
            return new CompletionRule() {
                public MissionCode code() { return code; }
                public boolean accepts(long userId,Mission mission,Receipt receipt) { return receipt.sourceKind().equals("SYNC_RESULT") && receipt.sourceId()==mission.id(); }
            };
        }
    }
    @Autowired JdbcTemplate db;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired AuthRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired ParticipationService service;
    @Autowired MissionCompletionService completion;
    @Autowired PlatformTransactionManager manager;
    @Autowired TestRestTemplate http;
    @MockitoBean MattermostClient mattermost;
    @MockitoBean(name="wishCompletionRule") CompletionRule actualWishRule;
    @MockitoBean(name="cheerCompletionRule") CompletionRule actualCheerRule;
    // This class tests the generic completion engine with its explicit fixture rules.
    @MockitoBean com.tagme.results.SyncCompletionRule syncCompletionRule;
    @MockitoBean com.tagme.results.TapCompletionRule tapCompletionRule;
    @MockitoSpyBean ParticipationRepository repository;
    static final String PASSWORD="a long test password";
    final Map<String,String> cookies=new HashMap<>();
    long alice; AuthPrincipal principal; String token;
    @BeforeEach void cleanDedicatedTestContainers() {
        reset(repository,mattermost); cookies.clear();
        for (String table:List.of("mission_completion_evidence","submission_receipts","event_completions","mission_progress","tags","user_consent_events","user_consents","user_interests","profiles","user_credentials","users","current_consent_policies","consent_policies","event_settings")) db.update("DELETE FROM "+table);
        db.update("UPDATE event_completion_sequence SET next_value=1 WHERE id=1"); db.update("UPDATE missions SET active=TRUE");
        try(var c=redis.getConnectionFactory().getConnection()) { c.serverCommands().flushDb(); }
        db.update("INSERT INTO event_settings(id,opens_at,submit_closes_at,moderation_closes_at) VALUES (1,UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 2 HOUR)");
        for(var type:PolicyType.values()) {
            db.update("INSERT INTO consent_policies VALUES (?, 'test-v1', '테스트 전용 제목', '테스트 전용 동의 문구')",type.name());
            db.update("INSERT INTO current_consent_policies VALUES (?, 'test-v1')",type.name());
        }
        alice=accounts.insertUser("a".repeat(26),"alice"); accounts.insertCredential(alice,passwords.encode(PASSWORD)); principal=accounts.byId(alice).orElseThrow().principal();
        accounts.syncProfile(alice,new MattermostProfile("테스트이름","서울",13,0));
        when(mattermost.byUsername("alice")).thenReturn(Optional.of(new MattermostClient.Identity("a".repeat(26),"alice",new MattermostProfile("테스트이름","서울",13,0))));
        token=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        db.update("INSERT INTO tags(mission_id,token_hash) VALUES (1,?)",ParticipationService.tokenHash(token));
    }
    List<Long> interestIds() { return db.queryForList("SELECT MIN(id) FROM interests WHERE active=TRUE GROUP BY category",Long.class); }
    ParticipationController.ProfileRequest request(boolean visible,long version) {
        return new ParticipationController.ProfileRequest("테스트별명","테스트 소개","https://github.com/example",null,interestIds(),
                Arrays.stream(PolicyType.values()).map(t -> new ParticipationController.Acceptance(t,"test-v1")).toList(),version);
    }
    Map<String,Object> payload(boolean visible,long version) {
        var p=new HashMap<String,Object>(); p.put("nickname","테스트별명");p.put("bio","테스트 소개");p.put("expectedVersion",version);p.put("interestIds",interestIds());
        p.put("acceptPolicies",Arrays.stream(PolicyType.values()).map(t -> Map.of("type",t.name(),"version","test-v1")).toList());return p;
    }
    ResponseEntity<Map> raw(HttpMethod method,String path,Object body,String csrf) {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        if(!cookies.isEmpty())headers.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).toList()));
        if(csrf!=null)headers.add("X-XSRF-TOKEN",csrf);
        var r=http.exchange("/api/v1"+path,method,new HttpEntity<>(body,headers),Map.class);
        for(String cookie:r.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)){String[] pair=cookie.split(";",2)[0].split("=",2);if(pair.length==2){if(pair[1].isEmpty())cookies.remove(pair[0]);else cookies.put(pair[0],pair[1]);}}return r;
    }
    ResponseEntity<Map> get(String path){return raw(HttpMethod.GET,path,null,null);}
    ResponseEntity<Map> send(HttpMethod method,String path,Object body){var csrf=get("/csrf");return raw(method,path,body,(String)csrf.getBody().get("token"));}
    void login(){assertThat(send(HttpMethod.POST,"/auth/login",Map.of("username","alice","password",PASSWORD)).getStatusCode().value()).isEqualTo(200);}
    @Test void anonymousTagInspectionReturnsOnlyActiveMissionMetadataWithoutUnlocking() {
        db.update("UPDATE tags SET mission_id=2");
        assertThat(raw(HttpMethod.POST,"/tags/inspect",Map.of("token",token),null).getStatusCode().value()).isEqualTo(403);
        var inspected=send(HttpMethod.POST,"/tags/inspect",Map.of("token",token));
        assertThat(inspected.getStatusCode().value()).isEqualTo(200);
        assertThat(inspected.getBody()).containsOnlyKeys("missionCode","letter","title").containsEntry("missionCode","SYNC_ME").containsEntry("letter","S");
        assertThat(get("/tags/pending").getBody()).containsEntry("present",false);
        assertThat(send(HttpMethod.POST,"/tags/resolve",Map.of("token",token)).getStatusCode().value()).isEqualTo(401);
        login();
        assertThat(send(HttpMethod.POST,"/tags/inspect",Map.of("token",token)).getStatusCode().value()).isEqualTo(200);
        assertThat(repository.mission(alice,MissionCode.SYNC_ME).state()).isEqualTo("LOCKED");
        for(String table:List.of("mission_progress","submission_receipts","mission_completion_evidence","event_completions"))
            assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
        db.update("UPDATE missions SET active=FALSE WHERE id=2");
        assertThat(send(HttpMethod.POST,"/tags/inspect",Map.of("token",token)).getStatusCode().value()).isEqualTo(404);
        db.update("UPDATE missions SET active=TRUE WHERE id=2");db.update("UPDATE tags SET active=FALSE");
        assertThat(send(HttpMethod.POST,"/tags/inspect",Map.of("token",token)).getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.POST,"/tags/inspect",Map.of("token","A".repeat(42)+"B")).getStatusCode().value()).isEqualTo(400);
    }
    @Test void inspectingPendingTagDoesNotConsumeIntentOrCreateProgress() {
        db.update("UPDATE tags SET mission_id=3");
        assertThat(send(HttpMethod.POST,"/tags/inspect",Map.of()).getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.POST,"/tags/pending",Map.of("token",token)).getStatusCode().value()).isEqualTo(202);
        assertThat(send(HttpMethod.POST,"/tags/inspect",Map.of()).getBody()).containsEntry("missionCode","MAKE_A_WISH").containsEntry("letter","A");
        assertThat(get("/tags/pending").getBody()).containsEntry("present",true);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_progress",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM submission_receipts",Integer.class)).isZero();
    }
    @Test void completeProfileAutomaticallyGrantsShowMeWithoutTagAndKeepsHistory() {
        service.save(principal,request(true,0));
        assertThat(repository.mission(alice,MissionCode.SHOW_ME).state()).isEqualTo("COMPLETED");
        var first=repository.mission(alice,MissionCode.SHOW_ME).completedAt();
        assertThat(db.queryForObject("SELECT source_kind FROM mission_completion_evidence WHERE user_id=?",String.class,alice)).isEqualTo("PROFILE");
        service.save(principal,request(true,1));
        assertThat(service.reconcileShowMe(principal)).isFalse();
        login(); assertThat(get("/missions/collection").getStatusCode().value()).isEqualTo(200);
        assertThat(repository.mission(alice,MissionCode.SHOW_ME).completedAt()).isEqualTo(first);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence WHERE user_id=?",Integer.class,alice)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM submission_receipts WHERE source_kind='TAG_UNLOCK'",Integer.class)).isZero();
        assertThat(repository.mission(alice,MissionCode.SYNC_ME).state()).isEqualTo("LOCKED");
    }
    @Test void preRegistrationReconcilesOnlyAfterOpenWithoutBackdatingOrGetWrites() {
        db.update("UPDATE event_settings SET opens_at=UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,submit_closes_at=UTC_TIMESTAMP(6)+INTERVAL 2 HOUR,moderation_closes_at=UTC_TIMESTAMP(6)+INTERVAL 3 HOUR,profile_preparation_enabled=FALSE");
        assertThatThrownBy(() -> service.save(principal,request(true,0))).isInstanceOf(AuthException.class);
        assertThat(repository.profile(alice)).isNull();
        db.update("UPDATE event_settings SET profile_preparation_enabled=TRUE");
        service.save(principal,request(true,0));
        assertThat(repository.mission(alice,MissionCode.SHOW_ME).state()).isEqualTo("LOCKED");
        assertThat(service.reconcileShowMe(principal)).isFalse();
        db.update("UPDATE event_settings SET opens_at=UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE");
        login(); get("/missions/collection");
        assertThat(repository.mission(alice,MissionCode.SHOW_ME).state()).isEqualTo("LOCKED");
        assertThat(raw(HttpMethod.POST,"/missions/show-me/reconcile",Map.of(),null).getStatusCode().value()).isEqualTo(403);
        Instant processing=repository.now();
        assertThat(send(HttpMethod.POST,"/missions/show-me/reconcile",Map.of()).getBody()).containsEntry("newlyCompleted",true);
        var accepted=db.queryForObject("SELECT accepted_received_at FROM mission_completion_evidence WHERE user_id=?",Timestamp.class,alice).toInstant();
        assertThat(accepted).isAfterOrEqualTo(processing);
        assertThat(repository.completion(alice).complete()).isFalse();
        assertThat(send(HttpMethod.POST,"/missions/show-me/reconcile",Map.of()).getBody()).containsEntry("newlyCompleted",false);
    }
    <T> T withDatabaseTime(Instant time,java.util.function.Supplier<T> action) {
        return new TransactionTemplate(manager).execute(status -> {
            db.execute("SET timestamp="+time.getEpochSecond());
            try { return action.get(); } finally { db.execute("SET timestamp=0"); }
        });
    }
    @Test void showMeUsesDatabaseTimeEvenWhenJvmClockDiffersAndHonoursOpenBoundary() {
        Instant opens=Instant.parse("2026-01-01T00:00:00Z"),closes=opens.plusSeconds(60);
        db.update("UPDATE event_settings SET opens_at=?,submit_closes_at=?,moderation_closes_at=?,profile_preparation_enabled=TRUE",Timestamp.from(opens),Timestamp.from(closes),Timestamp.from(closes.plusSeconds(60)));
        withDatabaseTime(opens.minusSeconds(1),() -> service.save(principal,request(true,0)));
        assertThat(repository.mission(alice,MissionCode.SHOW_ME).state()).isEqualTo("LOCKED");
        assertThat(withDatabaseTime(opens,() -> service.reconcileShowMe(principal))).isTrue();
        var granted=db.queryForObject("SELECT e.accepted_received_at,p.completed_at,p.unlocked_at,r.received_at FROM mission_completion_evidence e JOIN mission_progress p ON p.user_id=e.user_id AND p.mission_id=e.mission_id JOIN submission_receipts r ON r.id=e.receipt_order WHERE e.user_id=?",
            (row,n) -> new Instant[]{row.getTimestamp(1).toInstant(),row.getTimestamp(2).toInstant(),row.getTimestamp(3).toInstant(),row.getTimestamp(4).toInstant()},alice);
        assertThat(granted[0]).isEqualTo(opens);
        assertThat(granted[1]).isEqualTo(granted[0]);
        assertThat(granted[3]).isEqualTo(opens.minusSeconds(1));
        assertThat(granted[2]).isEqualTo(opens);
    }
    @Test void showMeCannotGrantAtTheExclusiveCloseBoundary() {
        Instant closes=Instant.parse("2026-01-01T00:01:00Z");
        db.update("UPDATE event_settings SET opens_at=?,submit_closes_at=?,moderation_closes_at=?,profile_preparation_enabled=TRUE",Timestamp.from(closes.minusSeconds(60)),Timestamp.from(closes),Timestamp.from(closes.plusSeconds(60)));
        withDatabaseTime(closes.minusSeconds(61),() -> service.save(principal,request(true,0)));
        assertThat(withDatabaseTime(closes,() -> service.reconcileShowMe(principal))).isFalse();
        assertThat(repository.mission(alice,MissionCode.SHOW_ME).state()).isEqualTo("LOCKED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isZero();
    }
    @Test void crossingCloseDuringShowMeRollsBackProfileUnlockAndEvidenceTogether() {
        Instant opens=Instant.parse("2026-01-01T00:00:00Z"),closes=opens.plusSeconds(60);
        db.update("UPDATE event_settings SET opens_at=?,submit_closes_at=?,moderation_closes_at=?",Timestamp.from(opens),Timestamp.from(closes),Timestamp.from(closes.plusSeconds(60)));
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if(reads.incrementAndGet()==2)db.execute("SET timestamp="+closes.getEpochSecond());
            return invocation.callRealMethod();
        }).when(repository).requireOpenTime();
        assertThatThrownBy(() -> withDatabaseTime(opens.plusSeconds(59),() -> service.save(principal,request(true,0))))
            .isInstanceOf(AuthException.class).hasMessageContaining("참여 가능한 시간");
        assertThat(repository.profile(alice)).isNull();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_progress",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM submission_receipts",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isZero();
    }
    @Test void csrfOwnershipFormatsMissingPolicyAndClosedEventFailWithoutProgress() {
        assertThat(send(HttpMethod.POST,"/missions/show-me/reconcile",Map.of()).getStatusCode().value()).isEqualTo(401);
        assertThat(send(HttpMethod.POST,"/tags/resolve",Map.of("token",token)).getStatusCode().value()).isEqualTo(401); login();
        assertThat(raw(HttpMethod.POST,"/tags/resolve",Map.of("token",token),null).getStatusCode().value()).isEqualTo(403);
        assertThat(send(HttpMethod.POST,"/tags/resolve",Map.of("token","SHOW_ME")).getStatusCode().value()).isEqualTo(400);
        String unknown=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]).replace('A','B');
        assertThat(send(HttpMethod.POST,"/tags/resolve",Map.of("token",unknown)).getStatusCode().value()).isEqualTo(400); // noncanonical trailing bits
        var extra=new HashMap<>(payload(true,0));extra.put("userId",999);assertThat(send(HttpMethod.PUT,"/users/me/profile",extra).getStatusCode().value()).isEqualTo(400);
        db.update("DELETE FROM current_consent_policies");
        assertThat(send(HttpMethod.PUT,"/users/me/profile",payload(true,0)).getStatusCode().value()).isEqualTo(503);
        assertThat(repository.profile(alice)).isNull();
        db.update("UPDATE event_settings SET opens_at=UTC_TIMESTAMP(6)-INTERVAL 3 HOUR,submit_closes_at=UTC_TIMESTAMP(6)-INTERVAL 2 HOUR,moderation_closes_at=UTC_TIMESTAMP(6)-INTERVAL 1 HOUR");
        assertThat(send(HttpMethod.POST,"/tags/resolve",Map.of("token",token)).getStatusCode().value()).isEqualTo(403);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_progress",Integer.class)).isZero();
    }
    @Test void pendingIntentSurvivesSessionRotationAndNeverExposesOrUnlocksTokenBeforeLogin() {
        assertThat(send(HttpMethod.POST,"/tags/pending",Map.of("token",token)).getStatusCode().value()).isEqualTo(202);
        String before=cookies.get("TAGME_SESSION");assertThat(get("/tags/pending").getBody()).containsEntry("present",true);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_progress",Integer.class)).isZero(); login();
        assertThat(cookies.get("TAGME_SESSION")).isNotEqualTo(before);
        var resumed=send(HttpMethod.POST,"/tags/resume",Map.of());assertThat(resumed.getStatusCode().value()).isEqualTo(200);
        assertThat(resumed.getBody().toString()).doesNotContain(token,"token_hash","real_name","mattermost");
        assertThat(get("/tags/pending").getBody()).containsEntry("present",false);
        assertThat(send(HttpMethod.POST,"/tags/resume",Map.of()).getStatusCode().value()).isEqualTo(404);
    }
    @Test void policyReplacementAndWithdrawalRequireFreshConsentButPreserveHistoricalCompletion() {
        service.save(principal,request(true,0));service.resolve(principal,ParticipationService.tokenHash(token));
        db.update("INSERT INTO consent_policies VALUES ('PROFILE_PUBLICATION','test-v2','테스트 변경','테스트 변경 문구')");
        db.update("UPDATE current_consent_policies SET policy_version='test-v2' WHERE policy_type='PROFILE_PUBLICATION'");
        assertThat(accounts.byId(alice).orElseThrow().onboardingRequired()).isTrue();
        assertThatThrownBy(() -> service.save(principal,request(true,1))).isInstanceOf(AuthException.class);
        db.update("UPDATE event_settings SET opens_at=UTC_TIMESTAMP(6)-INTERVAL 3 HOUR,submit_closes_at=UTC_TIMESTAMP(6)-INTERVAL 2 HOUR,moderation_closes_at=UTC_TIMESTAMP(6)-INTERVAL 1 HOUR");
        login();var disabled=send(HttpMethod.PUT,"/users/me/consents/PROFILE_PUBLICATION",Map.of("version","test-v1","agreed",false));
        assertThat(disabled.getStatusCode().value()).isEqualTo(404);
        assertThat(repository.profile(alice).isPublic()).isTrue();assertThat(repository.mission(alice,MissionCode.SHOW_ME).state()).isEqualTo("COMPLETED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM user_consent_events WHERE action='WITHDRAWN'",Integer.class)).isZero();
    }
    @Test void profileVersionAndUrlValidationNeverSilentlyOverwriteOrAllowScriptLinks() {
        login();assertThat(send(HttpMethod.PUT,"/users/me/profile",payload(false,0)).getStatusCode().value()).isEqualTo(200);
        assertThat(send(HttpMethod.PUT,"/users/me/profile",payload(true,0)).getStatusCode().value()).isEqualTo(409);
        var bad=payload(true,1);bad.put("blogUrl","javascript:alert(1)");assertThat(send(HttpMethod.PUT,"/users/me/profile",bad).getStatusCode().value()).isEqualTo(400);
        bad=payload(true,1);bad.put("interestIds",List.of(999999));assertThat(send(HttpMethod.PUT,"/users/me/profile",bad).getStatusCode().value()).isEqualTo(400);
        assertThat(repository.profile(alice).version()).isEqualTo(1);
    }
    @Test void twentyConcurrentProfileReconciliationsGrantExactlyOneLetter() throws Exception {
        db.update("UPDATE event_settings SET opens_at=UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,submit_closes_at=UTC_TIMESTAMP(6)+INTERVAL 2 HOUR,moderation_closes_at=UTC_TIMESTAMP(6)+INTERVAL 3 HOUR,profile_preparation_enabled=TRUE");
        service.save(principal,request(true,0));
        db.update("UPDATE event_settings SET opens_at=UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE");
        try(var pool=Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<20;i++) results.add(pool.submit(() -> service.reconcileShowMe(principal)));
            int completed=0;for(var future:results)if(future.get(30,TimeUnit.SECONDS))completed++;
            assertThat(completed).isEqualTo(1);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM submission_receipts WHERE source_kind='TAG_UNLOCK'",Integer.class)).isZero();
    }
    Receipt fixture(long userId,MissionCode code) {
        return new TransactionTemplate(manager).execute(status -> {
            repository.lockUser(userId);var mission=repository.mission(userId,code);Instant now=Instant.now();
            if("LOCKED".equals(mission.state()))repository.unlock(userId,mission.id(),now);
            return repository.receipt(userId,"SYNC_RESULT",mission.id(),now);
        });
    }
    @Test void differentFinalMissionsAllocateOneOrderAndRetriesKeepIt() throws Exception {
        service.save(principal,request(true,0));service.resolve(principal,ParticipationService.tokenHash(token));
        for(var code:List.of(MissionCode.SYNC_ME,MissionCode.MAKE_A_WISH)){var r=fixture(alice,code);completion.complete(alice,code,r.id());}
        var tap=fixture(alice,MissionCode.FAST_TAP);var cheer=fixture(alice,MissionCode.SAY_SOMETHING);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(() -> completion.complete(alice,MissionCode.FAST_TAP,tap.id()));var b=pool.submit(() -> completion.complete(alice,MissionCode.SAY_SOMETHING,cheer.id()));
            assertThat(a.get(30,TimeUnit.SECONDS)).isTrue();assertThat(b.get(30,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(repository.completion(alice).completionOrder()).isEqualTo(1);assertThat(repository.completion(alice).awardConfirmed()).isFalse();
        assertThat(completion.complete(alice,MissionCode.FAST_TAP,tap.id())).isFalse();
        assertThat(db.queryForObject("SELECT next_value FROM event_completion_sequence WHERE id=1",Long.class)).isEqualTo(2);
    }
    @Test void finalCompletionForDifferentUsersSerializesOnlyTheGlobalSequence() throws Exception {
        long bob=accounts.insertUser("b".repeat(26),"bob");accounts.insertCredential(bob,accounts.byId(alice).orElseThrow().hash());
        accounts.syncProfile(bob,new MattermostProfile("테스트이름","서울",13,0));
        Map<Long,Receipt> last=new HashMap<>();
        for(long userId:List.of(alice,bob)) {
            var actor=accounts.byId(userId).orElseThrow().principal();service.save(actor,request(true,0));service.resolve(actor,ParticipationService.tokenHash(token));
            for(var code:List.of(MissionCode.SYNC_ME,MissionCode.MAKE_A_WISH,MissionCode.FAST_TAP)){var receipt=fixture(userId,code);completion.complete(userId,code,receipt.id());}
            last.put(userId,fixture(userId,MissionCode.SAY_SOMETHING));
        }
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(() -> completion.complete(alice,MissionCode.SAY_SOMETHING,last.get(alice).id()));
            var b=pool.submit(() -> completion.complete(bob,MissionCode.SAY_SOMETHING,last.get(bob).id()));
            assertThat(a.get(30,TimeUnit.SECONDS)).isTrue();assertThat(b.get(30,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(List.of(repository.completion(alice).completionOrder(),repository.completion(bob).completionOrder())).containsExactlyInAnyOrder(1L,2L);
        assertThat(db.queryForObject("SELECT next_value FROM event_completion_sequence WHERE id=1",Long.class)).isEqualTo(3);
    }
    void legacyProfile() {
        db.update("INSERT INTO profiles(user_id,nickname,is_public) VALUES (?,'이전 프로필',TRUE)",alice);
        for(var acceptance:request(true,0).acceptPolicies())repository.agree(alice,acceptance.type(),acceptance.version());
    }
    @Test void inactiveAndUnknownTagsAndUnboundEvidenceCannotUnlockOrComplete() {
        var validUnknown=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0});
        assertThatThrownBy(() -> service.resolve(principal,ParticipationService.tokenHash(validUnknown))).isInstanceOf(AuthException.class).satisfies(e -> assertThat(((AuthException)e).status()).isEqualTo(404));
        db.update("UPDATE tags SET active=FALSE");
        assertThatThrownBy(() -> service.resolve(principal,ParticipationService.tokenHash(token))).isInstanceOf(AuthException.class).satisfies(e -> assertThat(((AuthException)e).status()).isEqualTo(404));
        db.update("UPDATE tags SET active=TRUE");db.update("UPDATE missions SET active=FALSE WHERE code='SHOW_ME'");
        assertThatThrownBy(() -> service.resolve(principal,ParticipationService.tokenHash(token))).isInstanceOf(AuthException.class).satisfies(e -> assertThat(((AuthException)e).status()).isEqualTo(403));
        db.update("UPDATE missions SET active=TRUE");
        legacyProfile();
        var created = new TransactionTemplate(manager).execute(status -> repository.receipt(alice,"PROFILE",1,Instant.now()));
        var receipt=created;
        assertThatThrownBy(() -> completion.complete(alice,MissionCode.SHOW_ME,receipt.id())).isInstanceOf(AuthException.class).satisfies(e -> assertThat(((AuthException)e).status()).isEqualTo(403));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_progress",Integer.class)).isZero();
    }
    @Test void preUnlockEvidenceForAnotherMissionCannotBePromotedByACompletionRule() {
        var earlier=fixture(alice,MissionCode.SYNC_ME);
        db.update("UPDATE submission_receipts SET received_at=received_at-INTERVAL 1 HOUR WHERE id=?",earlier.id());
        assertThatThrownBy(() -> completion.complete(alice,MissionCode.SYNC_ME,earlier.id())).isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException)e).code()).isEqualTo("INVALID_EVIDENCE"));
        assertThat(repository.mission(alice,MissionCode.SYNC_ME).state()).isEqualTo("UNLOCKED");
    }
    @Test void sequenceFailureRollsBackEvidenceProgressAndAllocation() {
        service.save(principal,request(true,0));service.resolve(principal,ParticipationService.tokenHash(token));
        for(var code:List.of(MissionCode.SYNC_ME,MissionCode.MAKE_A_WISH,MissionCode.FAST_TAP)){var r=fixture(alice,code);completion.complete(alice,code,r.id());}
        var last=fixture(alice,MissionCode.SAY_SOMETHING);
        doAnswer(invocation -> {invocation.callRealMethod();throw new org.springframework.dao.DataAccessResourceFailureException("test-only rollback after sequence update");})
                .when(repository).complete(eq(alice),any(),any(),any());
        assertThatThrownBy(() -> completion.complete(alice,MissionCode.SAY_SOMETHING,last.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(repository.mission(alice,MissionCode.SAY_SOMETHING).state()).isEqualTo("UNLOCKED");assertThat(repository.completion(alice).complete()).isFalse();
        assertThat(db.queryForObject("SELECT next_value FROM event_completion_sequence WHERE id=1",Long.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(4);
    }
    @Test void staleCredentialAndSuspendedUserCannotWriteAfterTheFilterCheck() {
        db.update("UPDATE user_credentials SET credential_version=credential_version+1 WHERE user_id=?",alice);
        assertThatThrownBy(() -> service.resolve(principal,ParticipationService.tokenHash(token))).isInstanceOf(AuthException.class);
        db.update("UPDATE users SET status='SUSPENDED' WHERE id=?",alice);
        assertThatThrownBy(() -> service.save(principal,request(true,0))).isInstanceOf(AuthException.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_progress",Integer.class)).isZero();assertThat(repository.profile(alice)).isNull();
    }
    @Test void importedIdentityCannotBeOverwrittenAndEveryCategoryAndBioAreRequired() {
        login();var body=payload(true,0);body.put("realName","위조 이름");assertThat(send(HttpMethod.PUT,"/users/me/profile",body).getStatusCode().value()).isEqualTo(400);
        body=payload(true,0);body.put("isPublic",false);assertThat(send(HttpMethod.PUT,"/users/me/profile",body).getStatusCode().value()).isEqualTo(400);
        body=payload(true,0);body.put("bio"," ");assertThat(send(HttpMethod.PUT,"/users/me/profile",body).getStatusCode().value()).isEqualTo(400);
        body=payload(true,0);body.put("interestIds",interestIds().subList(0,1));assertThat(send(HttpMethod.PUT,"/users/me/profile",body).getStatusCode().value()).isEqualTo(400);
        assertThat(service.save(principal,request(true,0)).participationReady()).isTrue();
        var retired=new java.util.ArrayList<>(interestIds());retired.add(db.queryForObject("SELECT MIN(id) FROM interests WHERE category='TECH_STACK' AND active=FALSE",Long.class));
        body=payload(true,1);body.put("interestIds",retired);assertThat(send(HttpMethod.PUT,"/users/me/profile",body).getStatusCode().value()).isEqualTo(400);
        assertThat(repository.interests()).hasSize(22);
        assertThat(repository.interests().stream().filter(i->i.category().equals("INDUSTRY"))).hasSize(10);
        assertThat(repository.interests().stream().filter(i->i.category().equals("DEVELOPMENT"))).hasSize(12);
        assertThat(repository.interests().stream().filter(i->i.category().equals("TECH_STACK"))).isEmpty();
    }
    @Test void showMeRequiresCompleteProfileAndPreservesItsFirstCompletionTime() {
        legacyProfile();
        assertThat(service.resolve(principal,ParticipationService.tokenHash(token)).newlyCompleted()).isFalse();
        assertThat(repository.mission(alice,MissionCode.SHOW_ME).state()).isEqualTo("LOCKED");
        var missing=new ParticipationController.ProfileRequest("테스트별명","소개",null,null,List.of(),request(true,0).acceptPolicies(),1);
        assertThatThrownBy(()->service.save(principal,missing)).isInstanceOf(AuthException.class);
        service.save(principal,new ParticipationController.ProfileRequest("테스트별명","소개",null,null,interestIds(),request(true,0).acceptPolicies(),1));
        var mission=repository.mission(alice,MissionCode.SHOW_ME);assertThat(mission.state()).isEqualTo("COMPLETED");
        assertThat(service.resolve(principal,ParticipationService.tokenHash(token)).newlyCompleted()).isFalse();
        assertThat(repository.mission(alice,MissionCode.SHOW_ME).completedAt()).isEqualTo(mission.completedAt());
    }
    @Test void finalShowMeValidatorCannotCompleteAnEmptyIntroductionThroughInternalEvidence() {
        legacyProfile(); var saved=service.profile(alice);
        service.resolve(principal, ParticipationService.tokenHash(token));
        assertThat(repository.eligible(alice)).isTrue();
        assertThat(repository.showMeEligible(alice)).isFalse();
        var receipt = new TransactionTemplate(manager).execute(status -> {
            repository.lockPrincipal(principal);
            repository.unlock(alice,repository.mission(alice,MissionCode.SHOW_ME).id(),Instant.now());
            return repository.receipt(alice, "PROFILE", saved.profile().version(), java.time.Instant.now());
        });
        assertThatThrownBy(() -> completion.complete(alice, MissionCode.SHOW_ME, receipt.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(failure -> assertThat(((AuthException)failure).code()).isEqualTo("MISSION_REQUIREMENTS"));
        assertThat(repository.mission(alice, MissionCode.SHOW_ME).state()).isEqualTo("UNLOCKED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence WHERE user_id=?", Integer.class, alice)).isZero();
    }
}
