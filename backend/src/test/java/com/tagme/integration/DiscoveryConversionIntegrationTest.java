package com.tagme.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.tagme.auth.*;
import com.tagme.operations.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@Tag("integration") @Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "tagme.admin.bootstrap-id=admin:integration","tagme.admin.bootstrap-password=only an integration password"})
class DiscoveryConversionIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));
        r.add("tagme.auth.enabled",()->true);r.add("tagme.auth.token",()->"integration-only-token");r.add("tagme.auth.channel-id",()->"c".repeat(26));
        r.add("tagme.auth.hmac-key",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @Autowired JdbcTemplate db; @Autowired AuthRepository accounts; @Autowired PasswordEncoder passwords;
    @Autowired LocalAdminService admins; @Autowired DiscoveryConversionAnalytics analytics; @Autowired ParticipationResetService resets;
    @Autowired TestRestTemplate http; @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @MockitoBean MattermostClient mattermost;
    AuthPrincipal admin; String hash;
    static final Instant COMPLETED=Instant.parse("2026-01-01T00:00:00Z");
    static final String PASSWORD="a participant test password";

    @BeforeEach void setup() {
        for(String table:List.of("admin_audit_logs","mission_completion_evidence","submission_receipts","event_completions","mission_progress",
                "game_participation_resets","connection_sessions","profile_views","sync_results","user_credentials","users")) db.update("DELETE FROM "+table);
        try(var connection=redis.getConnectionFactory().getConnection()) { connection.serverCommands().flushDb(); }
        admins.run(null);admin=accounts.byAdminLogin("admin:integration").orElseThrow().principal();hash=passwords.encode(PASSWORD);
        for(String version:List.of("conversion-test-v1","conversion-test-v2"))
            db.update("INSERT INTO sync_questionnaires(version,definition_json) VALUES (?,'{}') ON DUPLICATE KEY UPDATE version=version",version);
    }
    long participant(String name) {
        long id=accounts.insertUser("conversion-fixture-"+name,name);accounts.insertCredential(id,hash);return id;
    }
    void sync(long user,String version,Instant at) {
        db.update("INSERT INTO sync_results(user_id,questionnaire_version,answers_json,axis_scores_json,type_code,payload_hash,submitted_at) VALUES (?,?,'[]','{}','I',?,?)",
            user,version,"a".repeat(64),Timestamp.from(at));
    }
    void sync(long user) { sync(user,"conversion-test-v1",COMPLETED); }
    void visit(long viewer,long viewed,Instant at) {
        db.update("INSERT INTO profile_views(viewer_id,viewed_id,first_viewed_at) VALUES (?,?,?)",viewer,viewed,Timestamp.from(at));
    }
    DiscoveryConversionAnalytics.Report counts(long completed,long visited,long mutual) {
        var report=analytics.report(admin);
        assertThat(report.steps()).extracting(DiscoveryConversionAnalytics.Step::users).containsExactly(completed,visited,mutual);
        return report;
    }

    @Test void strictMicrosecondBoundaryCountsEachUserOnceAcrossPeersAndQuestionnaireVersions() {
        long before=participant("before"),equal=participant("equal"),after=participant("after"),many=participant("many"),peer=participant("peer"),other=participant("other");
        for(long user:List.of(before,equal,after)) sync(user);
        sync(many,"conversion-test-v2",COMPLETED);
        visit(before,peer,COMPLETED.minusNanos(1000));visit(equal,peer,COMPLETED);
        visit(after,peer,COMPLETED.plusNanos(1000));visit(many,peer,COMPLETED.plusSeconds(1));visit(many,other,COMPLETED.plusSeconds(2));
        var report=counts(4,2,0);
        assertThat(report.steps().get(1).previousConversionRate()).isEqualTo(50.0);
        assertThat(report.steps().get(2).previousConversionRate()).isZero();
        counts(4,2,0);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views",Integer.class)).isEqualTo(5);
    }

    @Test void mutualFormationMustUseTheSamePostCompletionOutgoingPeer() {
        long earlyInbound=participant("early-inbound"),lateInbound=participant("late-inbound"),oldOutgoing=participant("old-outgoing"),oldPair=participant("old-pair");
        long p1=participant("p1"),p2=participant("p2"),p3=participant("p3"),p4=participant("p4"),unrelated=participant("unrelated");
        for(long user:List.of(earlyInbound,lateInbound,oldOutgoing,oldPair)) sync(user);
        visit(earlyInbound,p1,COMPLETED.plusSeconds(1));visit(p1,earlyInbound,COMPLETED.minusSeconds(1));
        visit(lateInbound,p2,COMPLETED.plusSeconds(1));visit(p2,lateInbound,COMPLETED.plusSeconds(2));
        visit(oldOutgoing,p3,COMPLETED.minusSeconds(1));visit(p3,oldOutgoing,COMPLETED.plusSeconds(2));
        visit(oldOutgoing,unrelated,COMPLETED.plusSeconds(3));
        visit(oldPair,p4,COMPLETED.minusSeconds(2));visit(p4,oldPair,COMPLETED.minusSeconds(1));
        var report=counts(4,3,2);
        assertThat(report.steps().get(2).previousConversionRate()).isCloseTo(200.0/3,within(0.00001));
        assertThat(report.steps().get(2).completionConversionRate()).isEqualTo(50.0);
    }

    @Test void bothEndsExcludeInactiveAdminLocalOperatorAndUnregisteredAccounts() {
        long user=participant("active"),peer=participant("active-peer"),suspended=participant("suspended"),withdrawn=participant("withdrawn"),promoted=participant("promoted"),operator=participant("operator");
        long legacy=accounts.insertUser("conversion-fixture-legacy","legacy");
        db.update("UPDATE users SET status='SUSPENDED' WHERE id=?",suspended);db.update("UPDATE users SET status='WITHDRAWN' WHERE id=?",withdrawn);
        db.update("UPDATE users SET role='ADMIN' WHERE id=?",promoted);db.update("INSERT INTO local_admin_accounts VALUES (?,'admin:fixture-operator')",operator);
        sync(user);
        for(long excluded:List.of(suspended,withdrawn,promoted,operator,legacy,admin.userId())) {
            sync(excluded);visit(user,excluded,COMPLETED.plusSeconds(1));visit(excluded,user,COMPLETED.plusSeconds(2));
        }
        counts(1,0,0);
        visit(user,peer,COMPLETED.plusSeconds(1));visit(peer,user,COMPLETED.plusSeconds(2));counts(1,1,1);
        db.update("UPDATE users SET status='SUSPENDED' WHERE id=?",peer);counts(1,0,0);
    }

    @Test void realResetArchivesResultsButPreservesVisitsWithoutRevivingAnOldRound() {
        long user=participant("reset-user"),peer=participant("reset-peer"),freshPeer=participant("fresh-peer");
        sync(user);visit(user,peer,COMPLETED.plusSeconds(1));visit(peer,user,COMPLETED.plusSeconds(2));counts(1,1,1);
        resets.reset(admin,user,"conversion-reset-0001",new ParticipationResetService.ResetRequest(0,"synthetic conversion reset","RESET"));
        counts(0,0,0);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT JSON_LENGTH(snapshot_json,'$.sync') FROM game_participation_resets WHERE user_id=?",Integer.class,user)).isEqualTo(1);
        sync(user,"conversion-test-v2",COMPLETED.plusSeconds(10));counts(1,0,0);
        visit(user,freshPeer,COMPLETED.plusSeconds(11));visit(freshPeer,user,COMPLETED.plusSeconds(12));counts(1,1,1);
        // A generation bump for another mission retains this SYNC result and its conversion.
        db.update("INSERT INTO game_participation_resets(user_id,actor_id,generation,request_key,reason,snapshot_json,summary_json,operation,mission_code) VALUES (?,?,2,'conversion-other-mission','synthetic adjustment','{}','{}','GRANT','FAST_TAP')",user,admin.userId());
        counts(1,1,1);
    }

    @Test void emptyDataUsesNullRatesAndAManualMissionGrantCannotInventAResult() {
        var report=counts(0,0,0);
        assertThat(report.basis()).isEqualTo("CURRENT_SYNC_RESULTS_ALL_VERSIONS");assertThat(report.generatedAt()).isNotNull();
        assertThat(report.steps()).allSatisfy(step->{assertThat(step.previousConversionRate()).isNull();assertThat(step.completionConversionRate()).isNull();});
        long user=participant("manual-letter");
        db.update("INSERT INTO mission_progress(user_id,mission_id,status,unlocked_at,completed_at) SELECT ?,id,'COMPLETED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM missions WHERE code='SYNC_ME'",user);
        counts(0,0,0);
    }

    @Test void httpRequiresAdminAndCurrentCredentialsAndReturnsOnlyAggregateFields() {
        Browser anonymous=new Browser();assertThat(anonymous.call(HttpMethod.GET,"/api/v1/admin/discovery-conversion",null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        participant("http-user");
        when(mattermost.byUsername("http-user")).thenReturn(Optional.of(new MattermostClient.Identity(
            "conversion-fixture-http-user","http-user",new MattermostProfile("integration fixture","서울",1,0))));
        Browser user=new Browser();assertThat(user.login("http-user",PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.call(HttpMethod.GET,"/api/v1/admin/discovery-conversion",null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        Browser operator=new Browser();assertThat(operator.login("admin:integration","only an integration password").getStatusCode()).isEqualTo(HttpStatus.OK);
        var response=operator.call(HttpMethod.GET,"/api/v1/admin/discovery-conversion",null,null);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var keys=new HashSet<String>();response.getBody().fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("basis","generatedAt","steps");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views",Integer.class)).isZero();
        db.update("UPDATE users SET role='USER' WHERE id=?",admin.userId());
        assertThatThrownBy(()->analytics.report(admin)).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).status()).isEqualTo(403));
        assertThat(operator.call(HttpMethod.GET,"/api/v1/admin/discovery-conversion",null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        db.update("UPDATE users SET role='ADMIN' WHERE id=?",admin.userId());accounts.changePassword(admin.userId(),passwords.encode("rotated integration password"));
        assertThatThrownBy(()->analytics.report(admin)).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).status()).isEqualTo(401));
    }

    class Browser {
        final Map<String,String> cookies=new HashMap<>();
        ResponseEntity<JsonNode> call(HttpMethod method,String path,Object body,String csrf) {
            var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
            if(!cookies.isEmpty()) headers.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));
            if(csrf!=null) headers.add("X-XSRF-TOKEN",csrf);
            var response=http.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);
            for(String cookie:response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE,List.of())) {var pair=cookie.split(";",2)[0].split("=",2);cookies.put(pair[0],pair.length==2?pair[1]:"");}
            return response;
        }
        ResponseEntity<JsonNode> login(String username,String password) {
            String csrf=call(HttpMethod.GET,"/api/v1/csrf",null,null).getBody().get("token").asText();
            return call(HttpMethod.POST,"/api/v1/auth/login",Map.of("username",username,"password",password),csrf);
        }
    }
}
