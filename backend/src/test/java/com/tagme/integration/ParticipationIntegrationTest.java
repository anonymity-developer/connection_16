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
        when(mattermost.byUsername("alice")).thenReturn(Optional.of(new MattermostClient.Identity("a".repeat(26),"alice")));
        token=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        db.update("INSERT INTO tags(mission_id,token_hash) VALUES (1,?)",ParticipationService.tokenHash(token));
    }
    List<Long> interestIds() { return List.of(repository.interests().getFirst().id()); }
    ParticipationController.ProfileRequest request(boolean visible,long version) {
        return new ParticipationController.ProfileRequest("테스트별명","테스트이름",13,"테스트 소개","https://github.com/example",null,visible,interestIds(),
                Arrays.stream(PolicyType.values()).map(t -> new ParticipationController.Acceptance(t,"test-v1")).toList(),version);
    }
    Map<String,Object> payload(boolean visible,long version) {
        var p=new HashMap<String,Object>(); p.put("nickname","테스트별명");p.put("realName","테스트이름");p.put("classNo",13);p.put("bio","테스트 소개");p.put("isPublic",visible);p.put("expectedVersion",version);p.put("interestIds",interestIds());
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
    @Test void profileBeforeTagAndTagBeforeProfileOnlyCompleteAfterBothConditions() {
        service.save(principal,request(true,0)); assertThat(repository.mission(alice,MissionCode.SHOW_ME).state()).isEqualTo("LOCKED");
        assertThat(accounts.byId(alice).orElseThrow().onboardingRequired()).isFalse();
        var result=service.resolve(principal,ParticipationService.tokenHash(token)); assertThat(result.newlyCompleted()).isTrue();
        var first=repository.mission(alice,MissionCode.SHOW_ME).completedAt();
        assertThat(db.queryForObject("SELECT source_kind FROM mission_completion_evidence WHERE user_id=?",String.class,alice)).isEqualTo("TAG_UNLOCK");
        service.save(principal,request(true,1)); service.resolve(principal,ParticipationService.tokenHash(token));
        assertThat(repository.mission(alice,MissionCode.SHOW_ME).completedAt()).isEqualTo(first);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM user_consent_events",Integer.class)).isEqualTo(2);
        db.update("DELETE FROM mission_completion_evidence");db.update("DELETE FROM submission_receipts");db.update("DELETE FROM profiles");db.update("UPDATE mission_progress SET status='UNLOCKED',completed_at=NULL");
        service.save(principal,request(true,0)); assertThat(repository.mission(alice,MissionCode.SHOW_ME).state()).isEqualTo("COMPLETED");
        assertThat(db.queryForObject("SELECT source_kind FROM mission_completion_evidence WHERE user_id=?",String.class,alice)).isEqualTo("PROFILE");
    }
    @Test void csrfOwnershipFormatsMissingPolicyAndClosedEventFailWithoutProgress() {
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
        service.consent(principal,PolicyType.PROFILE_PUBLICATION,new ParticipationController.ConsentRequest("test-v1",false));
        assertThat(repository.profile(alice).isPublic()).isFalse();assertThat(repository.mission(alice,MissionCode.SHOW_ME).state()).isEqualTo("COMPLETED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM user_consent_events WHERE action='WITHDRAWN'",Integer.class)).isEqualTo(1);
    }
    @Test void profileVersionAndUrlValidationNeverSilentlyOverwriteOrAllowScriptLinks() {
        login();assertThat(send(HttpMethod.PUT,"/users/me/profile",payload(false,0)).getStatusCode().value()).isEqualTo(200);
        assertThat(send(HttpMethod.PUT,"/users/me/profile",payload(true,0)).getStatusCode().value()).isEqualTo(409);
        var bad=payload(true,1);bad.put("blogUrl","javascript:alert(1)");assertThat(send(HttpMethod.PUT,"/users/me/profile",bad).getStatusCode().value()).isEqualTo(400);
        bad=payload(true,1);bad.put("interestIds",List.of(999999));assertThat(send(HttpMethod.PUT,"/users/me/profile",bad).getStatusCode().value()).isEqualTo(400);
        assertThat(repository.profile(alice).version()).isEqualTo(1);
    }
    @Test void twentyConcurrentUnlocksHaveOneReceiptAndOneCompletion() throws Exception {
        service.save(principal,request(true,0));
        try(var pool=Executors.newFixedThreadPool(8)){
            List<Future<TagResult>> results=new ArrayList<>();for(int i=0;i<20;i++)results.add(pool.submit(() -> service.resolve(principal,ParticipationService.tokenHash(token))));
            int unlocked=0,completed=0;for(var future:results){var value=future.get(30,TimeUnit.SECONDS);if(value.newlyUnlocked())unlocked++;if(value.newlyCompleted())completed++;}
            assertThat(unlocked).isEqualTo(1);assertThat(completed).isEqualTo(1);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM submission_receipts WHERE source_kind='TAG_UNLOCK'",Integer.class)).isEqualTo(1);
    }
    Receipt fixture(long userId,MissionCode code) {
        return new TransactionTemplate(manager).execute(status -> {
            repository.lockUser(userId);var mission=repository.mission(userId,code);Instant now=Instant.now();
            if("LOCKED".equals(mission.state()))repository.unlock(userId,mission.id(),now);
            return repository.receipt(userId,"SYNC_RESULT",mission.id(),now);
        });
    }
    @Test void differentFinalMissionsAndUsersAllocateUniqueOrdersAndRetriesKeepThem() throws Exception {
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
}
