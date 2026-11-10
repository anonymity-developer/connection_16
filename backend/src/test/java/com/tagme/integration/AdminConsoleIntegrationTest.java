package com.tagme.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.auth.*;
import com.tagme.operations.*;
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
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration") @Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"tagme.auth.enabled=false","tagme.admin.bootstrap-id=admin:integration","tagme.admin.bootstrap-password=only an integration password","tagme.content.worker-enabled=false"})
class AdminConsoleIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));r.add("tagme.auth.hmac-key",()->Base64.getEncoder().encodeToString(new byte[32]));}
    @Autowired JdbcTemplate db;@Autowired AuthRepository accounts;@Autowired LocalAdminService admins;@Autowired AdminAnalytics analytics;@Autowired PasswordEncoder passwords;@Autowired TestRestTemplate http;@Autowired ObjectMapper json;@Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @MockitoBean MattermostClient mattermost;
    AuthPrincipal admin;String hash;
    @BeforeEach void setup(){
        reset(mattermost);db.update("UPDATE content_items SET approved_revision_id=NULL,pending_revision_id=NULL");
        for(String table:List.of("admin_audit_logs","cheer_likes","wish_comments","moderation_reviews","moderation_jobs","content_revisions","content_items","content_settings","mission_completion_evidence","submission_receipts","event_completions","mission_progress","tags","sync_results","current_sync_questionnaire","sync_questionnaires","tap_attempt_events","tap_slots","tap_attempts","profile_views","user_consent_events","user_consents","user_interests","profiles","user_credentials","users","current_consent_policies","consent_policies","event_settings"))db.update("DELETE FROM "+table);
        try(var c=redis.getConnectionFactory().getConnection()){c.serverCommands().flushDb();}
        admins.run(null);admin=accounts.byAdminLogin("admin:integration").orElseThrow().principal();hash=passwords.encode("a participant test password");
        for(String type:List.of("PARTICIPATION","PROFILE_PUBLICATION")){db.update("INSERT INTO consent_policies VALUES(?,'test-v1','integration policy','integration fixture only')",type);db.update("INSERT INTO current_consent_policies VALUES(?,'test-v1')",type);}
    }
    long participant(String name){long id=accounts.insertUser(name+"-integration-fixture",name);accounts.insertCredential(id,hash);db.update("UPDATE users SET real_name=?,region='서울',class_no=1,mattermost_profile_synced_at=UTC_TIMESTAMP(6) WHERE id=?",name,id);db.update("INSERT INTO profiles(user_id,nickname,bio,is_public) VALUES(?,?,?,TRUE)",id,name,"integration fixture");db.update("INSERT INTO user_consents(user_id,policy_type,policy_version,agreed_at) SELECT ?,policy_type,policy_version,UTC_TIMESTAMP(6) FROM current_consent_policies",id);return id;}
    void complete(long user,String code){db.update("INSERT INTO mission_progress(user_id,mission_id,status,unlocked_at,completed_at) SELECT ?,id,'COMPLETED',UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE FROM missions WHERE code=?",user,code);}
    long content(long user,String kind,boolean approved){db.update("INSERT INTO content_items(author_id,kind) VALUES(?,?)",user,kind);long id=db.queryForObject("SELECT MAX(id) FROM content_items WHERE author_id=?",Long.class,user);db.update("INSERT INTO content_revisions(item_id,revision_no,content_text,template_id,style,anonymous,submitted_at,moderation_status,idempotency_key,payload_hash) VALUES(?,1,'integration fixture','PLAIN','INK',FALSE,UTC_TIMESTAMP(6),?,'integration-fixture-key',?)",id,approved?"APPROVED":"REVIEW_REQUIRED","a".repeat(64));long revision=db.queryForObject("SELECT id FROM content_revisions WHERE item_id=?",Long.class,id);if(approved)db.update("UPDATE content_items SET approved_revision_id=? WHERE id=?",revision,id);return id;}
    void tap(long user,int count,String status){db.update("INSERT INTO tap_attempts(user_id,generation,idempotency_key,starts_at,ends_at,submit_deadline,status,tap_count,submitted_at) VALUES(?,1,'integration-tap-key',UTC_TIMESTAMP(6)-INTERVAL 30 SECOND,UTC_TIMESTAMP(6)-INTERVAL 20 SECOND,UTC_TIMESTAMP(6)-INTERVAL 15 SECOND,?,?,UTC_TIMESTAMP(6)-INTERVAL 19 SECOND)",user,status,count);long id=db.queryForObject("SELECT id FROM tap_attempts WHERE user_id=?",Long.class,user);db.update("INSERT INTO tap_slots(user_id,current_attempt_id) VALUES(?,?)",user,id);}
    @Test void administratorLoginIsIndependentOfMattermostAndRetainsSessionSecurity(){
        Browser b=new Browser();assertThat(b.call(HttpMethod.POST,"/api/v1/auth/login",Map.of("username","admin:integration","password","only an integration password"),null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var config=b.call(HttpMethod.GET,"/api/v1/auth/config",null,null);assertThat(config.getBody()).containsEntry("participantAvailable",false).containsEntry("adminAvailable",true);
        var wrong=b.login("wrong integration password");assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var login=b.login("only an integration password");assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(login.getBody()).containsEntry("admin",true).containsEntry("onboardingRequired",false).doesNotContainKeys("password","hash","mattermostId");
        assertThat(b.call(HttpMethod.GET,"/api/v1/admin/overview",null,null).getBody()).containsEntry("members",0);
        verifyNoInteractions(mattermost);
        db.update("UPDATE users SET role='USER' WHERE id=?",admin.userId());assertThat(b.call(HttpMethod.GET,"/api/v1/admin/overview",null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void paginationSearchSignupOrderAndCompletionOrderAreBasedOnActualRecords(){
        long first=participant("alpha"),second=participant("beta");db.update("UPDATE users SET created_at='2026-01-01 00:00:00' WHERE id=?",first);db.update("UPDATE users SET created_at='2026-01-02 00:00:00' WHERE id=?",second);
        complete(second,"SHOW_ME");db.update("INSERT INTO event_completions(user_id,completion_order,completed_at) VALUES(?,7,UTC_TIMESTAMP(6))",second);
        var page=analytics.members(admin,0,1,null,false);assertThat(page.total()).isEqualTo(2);assertThat(page.items().getFirst().id()).isEqualTo(first);assertThat(page.items().getFirst().signupOrder()).isEqualTo(1);assertThat(page.nextOffset()).isEqualTo(1);
        var search=analytics.members(admin,0,25,"beta",false);assertThat(search.items().getFirst().signupOrder()).isEqualTo(2);assertThat(analytics.members(admin,0,25,"%",false).items()).isEmpty();
        assertThat(analytics.members(admin,0,25,null,true).items().getFirst().completionOrder()).isEqualTo(7);assertThat(analytics.overview(admin).members()).isEqualTo(2);assertThat(analytics.detail(admin,second).missions()).hasSize(5);
        assertThatThrownBy(()->analytics.overview(accounts.byId(first).orElseThrow().principal())).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).status()).isEqualTo(403));
    }
    @Test void overviewRetainsAllFiveMissionsWhenOnlyNonParticipantsHaveProgress(){
        long imported=accounts.insertUser("imported-only-integration-fixture","imported");
        complete(imported,"SYNC_ME");complete(admin.userId(),"FAST_TAP");
        var overview=analytics.overview(admin);
        assertThat(overview.members()).isZero();assertThat(overview.missions()).hasSize(5);
        assertThat(overview.missions()).allSatisfy(m->{assertThat(m.unlocked()).isZero();assertThat(m.participated()).isZero();assertThat(m.completed()).isZero();});
    }
    @Test void rankingsExcludeInvalidScoresOwnReactionsAndHiddenOrIneligibleActivities(){
        long a=participant("alpha"),b=participant("beta"),c=participant("gamma");
        for(long id:List.of(a,b,c)){complete(id,"FAST_TAP");complete(id,"MAKE_A_WISH");complete(id,"SAY_SOMETHING");}
        tap(a,20,"VALID");tap(b,999,"REVIEW_REQUIRED");tap(c,30,"VALID");assertThat(analytics.ranking(admin,"FAST_TAP",0,25).items()).extracting(AdminAnalytics.Rank::userId).containsExactly(c,a);
        long wish=content(a,"WISH",true),own=content(a,"COMMENT",true),other=content(b,"COMMENT",true);db.update("INSERT INTO wish_comments(wish_id,content_item_id,author_id,idempotency_key,payload_hash) VALUES(?,?,?,'self-test-key',?)",wish,own,a,"a".repeat(64));db.update("INSERT INTO wish_comments(wish_id,content_item_id,author_id,idempotency_key,payload_hash) VALUES(?,?,?,'other-test-key',?)",wish,other,b,"b".repeat(64));
        assertThat(analytics.ranking(admin,"MAKE_A_WISH",0,25).items().getFirst().metric()).isEqualTo(1);
        long cheer=content(a,"CHEER",true);db.update("INSERT INTO cheer_likes(card_id,user_id) VALUES(?,?),(?,?)",cheer,a,cheer,b);assertThat(analytics.ranking(admin,"SAY_SOMETHING",0,25).items().getFirst().metric()).isEqualTo(1);
        db.update("UPDATE users SET status='SUSPENDED' WHERE id=?",b);assertThat(analytics.ranking(admin,"MAKE_A_WISH",0,25).items().getFirst().metric()).isZero();assertThat(analytics.ranking(admin,"SAY_SOMETHING",0,25).items().getFirst().metric()).isZero();
        db.update("UPDATE content_items SET visibility_status='HIDDEN' WHERE id IN (?,?)",wish,cheer);assertThat(analytics.ranking(admin,"MAKE_A_WISH",0,25).items()).isEmpty();assertThat(analytics.ranking(admin,"SAY_SOMETHING",0,25).items()).isEmpty();
    }
    @Test void connectionDurationsCapAtExpiryAndDoNotInventHistoricalData(){
        long a=participant("alpha"),b=participant("beta");
        db.update("INSERT INTO connection_sessions(id,user_id,credential_version,started_at,last_seen_at,expires_at) VALUES(?, ?,1,UTC_TIMESTAMP(6)-INTERVAL 2 HOUR,UTC_TIMESTAMP(6)-INTERVAL 2 HOUR,UTC_TIMESTAMP(6)-INTERVAL 1 HOUR)",UUID.randomUUID().toString(),a);
        db.update("INSERT INTO connection_sessions(id,user_id,credential_version,started_at,last_seen_at,expires_at,ended_at,end_reason) VALUES(?, ?,1,UTC_TIMESTAMP(6)-INTERVAL 10 MINUTE,UTC_TIMESTAMP(6)-INTERVAL 6 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6)-INTERVAL 5 MINUTE,'LOGOUT')",UUID.randomUUID().toString(),a);
        var row=analytics.detail(admin,a);assertThat(row.member().connectionSeconds()).isEqualTo(3900);assertThat(row.connections()).extracting(AdminAnalytics.Connection::status).containsExactly("LOGOUT","EXPIRED");assertThat(analytics.detail(admin,b).member().connectionSeconds()).isNull();assertThat(analytics.overview(admin).connectionSeconds()).isEqualTo(3900);
    }
    @Test void previewRequiresAdminAndDoesNotCreateOfficialRecords(){
        Browser b=new Browser();assertThat(b.call(HttpMethod.GET,"/api/v1/admin/preview/sync",null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);b.login("only an integration password");
        var definition=b.call(HttpMethod.GET,"/api/v1/admin/preview/sync",null,null);assertThat(definition.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(definition.getBody()).containsEntry("source","EXAMPLE");
        for(String table:List.of("mission_progress","sync_results","tap_attempts","content_items","event_completions"))assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
        String csrf=b.csrf();assertThat(b.call(HttpMethod.POST,"/api/v1/tap/start",null,csrf).getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }
    @Test void passwordRotationRevokesExistingSessionsWithoutContactingMattermost(){Browser b=new Browser();b.login("only an integration password");var a=accounts.byId(admin.userId()).orElseThrow();accounts.changePassword(a.id(),passwords.encode("rotated integration password"));assertThat(b.call(HttpMethod.GET,"/api/v1/admin/overview",null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);verifyNoInteractions(mattermost);}
    @Test void activeSessionsExcludeSuspendedAccountsAndStaleCredentialVersions(){
        long id=participant("alpha");db.update("INSERT INTO connection_sessions(id,user_id,credential_version,expires_at) VALUES(?,?,1,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR)",UUID.randomUUID().toString(),id);
        assertThat(analytics.overview(admin).activeSessions()).isEqualTo(1);db.update("UPDATE users SET status='SUSPENDED' WHERE id=?",id);assertThat(analytics.overview(admin).activeSessions()).isZero();assertThat(analytics.detail(admin,id).member().activeSessions()).isZero();db.update("UPDATE users SET status='ACTIVE' WHERE id=?",id);db.update("UPDATE user_credentials SET credential_version=2 WHERE user_id=?",id);assertThat(analytics.overview(admin).activeSessions()).isZero();
    }
    class Browser {
        final Map<String,String> cookies=new HashMap<>();
        ResponseEntity<Map> call(HttpMethod method,String path,Object body,String csrf){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(!cookies.isEmpty())h.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));if(csrf!=null)h.add("X-XSRF-TOKEN",csrf);var response=http.exchange(path,method,new HttpEntity<>(body,h),Map.class);for(String cookie:response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE,List.of())){String[] pair=cookie.split(";",2)[0].split("=",2);cookies.put(pair[0],pair.length==2?pair[1]:"");}return response;}
        String csrf(){return (String)call(HttpMethod.GET,"/api/v1/csrf",null,null).getBody().get("token");}
        ResponseEntity<Map> login(String password){return call(HttpMethod.POST,"/api/v1/auth/login",Map.of("username","admin:integration","password",password),csrf());}
    }
}
