package com.tagme.integration;

import com.tagme.auth.*;
import com.tagme.operations.*;
import com.tagme.participation.ParticipationRepository;
import java.sql.Timestamp;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

/** Isolated fixtures only. No MM, live participant data or environment-provided database URL. */
@Tag("integration") @Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"tagme.content.worker-enabled=false","tagme.moderation.enabled=true"})
class PostEventIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withUrlParam("connectionTimeZone","UTC").withUrlParam("forceConnectionTimeZoneToSession","true");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);p.add("spring.flyway.url",MYSQL::getJdbcUrl);p.add("spring.flyway.user",MYSQL::getUsername);p.add("spring.flyway.password",MYSQL::getPassword);p.add("tagme.auth.enabled",()->true);p.add("tagme.auth.token",()->"isolated-mock-only");p.add("tagme.auth.channel-id",()->"c".repeat(26));p.add("tagme.auth.hmac-key",()->Base64.getEncoder().encodeToString(new byte[32]));p.add("spring.data.redis.host",REDIS::getHost);p.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));}
    @Autowired JdbcTemplate db;@Autowired AuthRepository accounts;@Autowired PasswordEncoder passwords;@Autowired PostEventService results;@Autowired OperationsRepository operations;@Autowired ParticipationRepository participation;@Autowired TestRestTemplate http;
    @MockitoSpyBean PostEventRepository repository;@MockitoBean MattermostClient mattermost;
    AuthPrincipal admin,user;long uid;String adminHash;
    @BeforeEach void setup(){
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(MYSQL.getDatabaseName());reset(repository,mattermost);
        db.update("UPDATE content_items SET approved_revision_id=NULL,pending_revision_id=NULL");
        for(String t:List.of("admin_audit_logs","profile_views","cheer_likes","wish_comments","moderation_reviews","moderation_jobs","content_revisions","content_items","content_settings","mission_completion_evidence","submission_receipts","event_completions","mission_progress","tap_attempt_events","tap_slots","tap_attempts","sync_results","current_sync_questionnaire","sync_questionnaires","tags","user_consent_events","user_consents","user_interests","profiles","user_credentials","users","current_consent_policies","consent_policies","event_settings"))db.update("DELETE FROM "+t);
        long aid=accounts.insertUser("z".repeat(26),"fixture-admin");db.update("UPDATE users SET role='ADMIN' WHERE id=?",aid);adminHash=passwords.encode("isolated fixture long password");accounts.insertCredential(aid,adminHash);admin=accounts.byId(aid).orElseThrow().principal();
        when(mattermost.byUsername("fixture-admin")).thenReturn(Optional.of(new MattermostClient.Identity("z".repeat(26),"fixture-admin",new MattermostProfile("TEST ONLY","서울",1,0))));
        when(mattermost.byUsername("fixture-a")).thenReturn(Optional.of(new MattermostClient.Identity("a".repeat(26),"fixture-a",new MattermostProfile("TEST ONLY","서울",1,0))));
    }
    long metric(PostEventModels.Report r,String key){return r.data().metrics().stream().filter(m->m.key().equals(key)).findFirst().orElseThrow().value();}
    long person(String letter,String status){long id=accounts.insertUser(letter.repeat(26),"fixture-"+letter);db.update("UPDATE users SET status=? WHERE id=?",status,id);accounts.insertCredential(id,adminHash);return id;}
    void fixture(){
        uid=person("a","ACTIVE");user=accounts.byId(uid).orElseThrow().principal();person("b","ACTIVE");person("w","WITHDRAWN");
        db.update("INSERT INTO event_settings(id,opens_at,submit_closes_at,moderation_closes_at) VALUES (1,UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 2 HOUR)");
        db.update("UPDATE users SET real_name='TEST ONLY',region='서울',class_no=1,mattermost_profile_synced_at=UTC_TIMESTAMP(6) WHERE id=?",uid);
        db.update("INSERT INTO profiles(user_id,nickname,bio,is_public,published_at) VALUES (?,'=NOT_EXPORTED','TEST ONLY',TRUE,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE)",uid);db.update("INSERT INTO user_interests SELECT ?,MIN(id) FROM interests WHERE active=TRUE GROUP BY category",uid);
        for(String type:List.of("PARTICIPATION","PROFILE_PUBLICATION")){db.update("INSERT INTO consent_policies VALUES (?,'test-only','TEST','TEST')",type);db.update("INSERT INTO current_consent_policies VALUES (?,'test-only')",type);db.update("INSERT INTO user_consents(user_id,policy_type,policy_version,agreed_at) VALUES (?,?,'test-only',UTC_TIMESTAMP(6))",uid,type);}
        db.update("INSERT INTO mission_progress(user_id,mission_id,status,unlocked_at,completed_at) VALUES (?,1,'COMPLETED',UTC_TIMESTAMP(6)-INTERVAL 30 SECOND,UTC_TIMESTAMP(6)-INTERVAL 20 SECOND)",uid);
        db.update("INSERT INTO submission_receipts(user_id,source_kind,source_id,received_at) VALUES (?,'PROFILE',1,UTC_TIMESTAMP(6)-INTERVAL 25 SECOND)",uid);
        db.update("INSERT INTO mission_completion_evidence SELECT ?,1,'PROFILE',1,id,received_at FROM submission_receipts WHERE user_id=?",uid,uid);
    }
    @Test void emptyEventProducesZeroesMissingWindowAndNoFinalization(){var r=results.report(admin);assertThat(metric(r,"registered")).isZero();assertThat(r.data().phase()).isEqualTo("NOT_CONFIGURED");assertThat(r.data().candidates()).isEmpty();assertThat(r.data().finalizationAvailable()).isFalse();assertThat(PostEventExport.csv(r)).contains("SUMMARY").doesNotContain("fixture-admin","mattermost","password");}
    @Test void partialCohortPrivacyChangesAndSnapshotChangeAreExplicit(){fixture();var first=results.report(admin);assertThat(metric(first,"registered")).isEqualTo(2);assertThat(metric(first,"profileReady")).isEqualTo(1);assertThat(metric(first,"earnedAny")).isEqualTo(1);assertThat(metric(first,"earnedFive")).isZero();assertThat(first.data().missions().getFirst().unlocked()).isNull();assertThat(results.report(admin).snapshotId()).isEqualTo(first.snapshotId());
        db.update("UPDATE profiles SET is_public=FALSE WHERE user_id=?",uid);var changed=results.report(admin);assertThat(metric(changed,"profileReady")).isZero();assertThat(metric(changed,"earnedAny")).isEqualTo(1);assertThat(changed.snapshotId()).isNotEqualTo(first.snapshotId());assertThatThrownBy(()->results.forExport(admin,first.snapshotId())).isInstanceOf(AuthException.class);
        db.update("UPDATE users SET status='WITHDRAWN' WHERE id=?",uid);assertThat(metric(results.report(admin),"registered")).isEqualTo(1);assertThatThrownBy(()->results.history(admin,uid,"RESULT_RECONCILIATION",0)).isInstanceOf(AuthException.class);
    }
    @Test void currentPendingHiddenAndHeartCancellationDoNotMasqueradeAsPublicApproval(){fixture();
        db.update("INSERT INTO content_items(author_id,kind,visibility_status) VALUES (?,'CHEER','HIDDEN')",uid);long item=db.queryForObject("SELECT id FROM content_items WHERE author_id=?",Long.class,uid);
        db.update("INSERT INTO content_revisions(item_id,revision_no,content_text,template_id,style,anonymous,submitted_at,idempotency_key,payload_hash) VALUES (?,1,'DO NOT EXPORT','PLAIN','ROSE',TRUE,UTC_TIMESTAMP(6),'post-event-fixture-key',REPEAT('a',64))",item);long rev=db.queryForObject("SELECT id FROM content_revisions WHERE item_id=?",Long.class,item);
        db.update("UPDATE content_items SET pending_revision_id=? WHERE id=?",rev,item);db.update("INSERT INTO moderation_jobs(revision_id) VALUES (?)",rev);
        db.update("INSERT INTO cheer_likes(card_id,user_id) VALUES (?,?)",item,uid);var r=results.report(admin);assertThat(metric(r,"publicCHEER")).isZero();assertThat(metric(r,"validHearts")).isZero();assertThat(metric(r,"pendingModerationAll")).isEqualTo(1);assertThat(r.data().buckets()).anyMatch(b->b.group().equals("HIDDEN_ITEMS")&&b.count()==1);
        db.update("DELETE FROM cheer_likes WHERE card_id=?",item);assertThat(metric(results.report(admin),"validHearts")).isZero();assertThat(PostEventExport.csv(r)).doesNotContain("DO NOT EXPORT","=NOT_EXPORTED");
    }
    @Test void permissionUsesCurrentDbRoleAndIndividualExportDoesNotExist(){fixture();assertThatThrownBy(()->results.report(user)).isInstanceOf(AuthException.class);assertThatThrownBy(()->results.history(user,uid,"RESULT_RECONCILIATION",0)).isInstanceOf(AuthException.class);assertThatThrownBy(()->results.forExport(user,"invalid")).isInstanceOf(AuthException.class);assertThatThrownBy(()->results.history(admin,uid,"UNAPPROVED",0)).isInstanceOf(AuthException.class);
        for(String path:List.of("/api/v1/admin/results","/api/v1/admin/results/export?format=csv&snapshot=x","/api/v1/admin/results/participants/1?purpose=RESULT_RECONCILIATION"))assertThat(http.getForEntity(path,String.class).getStatusCode().value()).isEqualTo(401);
        db.update("UPDATE users SET role='USER' WHERE id=?",admin.userId());assertThatThrownBy(()->results.report(admin)).isInstanceOf(AuthException.class);
    }
    @Test void laterWritesCannotMixWithEarlierRankingWithinOneSnapshot(){fixture();var once=new AtomicBoolean(true);doAnswer(inv->{if(once.getAndSet(false))CompletableFuture.runAsync(()->person("n","ACTIVE")).join();return inv.callRealMethod();}).when(repository).metrics(any());var first=results.report(admin);assertThat(metric(first,"registered")).isEqualTo(2);assertThat(metric(results.report(admin),"registered")).isEqualTo(3);}
    @Test void extractionDesignUsesRealServerTimesAndNoShowNfcOrPersonalColumns()throws Exception{fixture();
        String sql=new String(getClass().getResourceAsStream("/post-event/activity-extract.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);Instant at=repository.now();var rows=new NamedParameterJdbcTemplate(db).queryForList(sql,Map.of("observed_at",Timestamp.from(at),"from_at",Timestamp.from(at.minusSeconds(7200)),"until_at",Timestamp.from(at)));
        assertThat(rows).isNotEmpty();assertThat(rows).noneMatch(r->"NFC_FIRST_UNLOCK".equals(r.get("event_type"))&&"SHOW_ME".equals(r.get("mission_code")));assertThat(rows).anyMatch(r->"LETTER_GRANTED".equals(r.get("event_type")));assertThat(rows).allSatisfy(r->assertThat(r.keySet()).containsExactlyInAnyOrder("internal_user_id","event_type","mission_code","event_time","status","source_type","record_key"));
        assertThat(results.history(admin,uid,"RESULT_RECONCILIATION",0).missions()).hasSize(5);
    }
    HttpHeaders login(String name){
        var cookies=new HashMap<String,String>();var headers=new HttpHeaders();
        var csrf=http.getForEntity("/api/v1/csrf",JsonNode.class);capture(csrf.getHeaders(),cookies);headers.set(HttpHeaders.COOKIE,cookie(cookies));headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-XSRF-TOKEN",csrf.getBody().path("token").asText());
        var login=http.exchange("/api/v1/auth/login",HttpMethod.POST,new HttpEntity<>(Map.of("username",name,"password","isolated fixture long password"),headers),JsonNode.class);assertThat(login.getStatusCode().value()).isEqualTo(200);capture(login.getHeaders(),cookies);
        var result=new HttpHeaders();result.set(HttpHeaders.COOKIE,cookie(cookies));return result;
    }
    static String cookie(Map<String,String> cookies){return cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("; "));}
    static void capture(HttpHeaders headers,Map<String,String> cookies){for(String value:headers.getOrEmpty(HttpHeaders.SET_COOKIE)){var pair=value.split(";",2)[0].split("=",2);if(pair.length==2)cookies.put(pair[0],pair[1]);}}
    @Test void actualHttpDownloadsEnforceRoleFingerprintContentAndNoStore(){fixture();var headers=login("fixture-admin");
        var response=http.exchange("/api/v1/admin/results",HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);assertThat(response.getStatusCode().value()).isEqualTo(200);String fingerprint=response.getBody().path("snapshotId").asText();
        for(String format:List.of("csv","json","md")){var export=http.exchange("/api/v1/admin/results/export?format="+format+"&snapshot="+fingerprint,HttpMethod.GET,new HttpEntity<>(headers),String.class);assertThat(export.getStatusCode().value()).isEqualTo(200);assertThat(export.getHeaders().getCacheControl()).contains("no-store");assertThat(export.getHeaders().getFirst("Content-Disposition")).contains("attachment");assertThat(export.getBody()).doesNotContain("TEST ONLY","NOT_EXPORTED","fixture-admin","mattermost_id","content_text");}
        var normal=login("fixture-a");assertThat(http.exchange("/api/v1/admin/results/export?format=csv&snapshot="+fingerprint,HttpMethod.GET,new HttpEntity<>(normal),String.class).getStatusCode().value()).isEqualTo(403);
        person("x","ACTIVE");assertThat(http.exchange("/api/v1/admin/results/export?format=csv&snapshot="+fingerprint,HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode().value()).isEqualTo(409);
    }

    @Test void invalidQuestionnaireAndTapReviewRemainExplicitRatherThanInventedResults(){fixture();
        db.update("INSERT INTO sync_questionnaires VALUES ('broken-test',?)","{\"title\":\"invalid fixture\"}");db.update("INSERT INTO current_sync_questionnaire VALUES (1,'broken-test')");
        db.update("INSERT INTO sync_results(user_id,questionnaire_version,answers_json,axis_scores_json,type_code,payload_hash,submitted_at) VALUES (?,'broken-test','[]','{}','X',REPEAT('a',64),UTC_TIMESTAMP(6))",uid);
        db.update("INSERT INTO tap_attempts(user_id,generation,idempotency_key,starts_at,ends_at,submit_deadline,status,tap_count,submitted_at,payload_hash) VALUES (?,1,'post-event-review-tap',UTC_TIMESTAMP(6)-INTERVAL 20 SECOND,UTC_TIMESTAMP(6)-INTERVAL 10 SECOND,UTC_TIMESTAMP(6)+INTERVAL 10 SECOND,'REVIEW_REQUIRED',300,UTC_TIMESTAMP(6),REPEAT('b',64))",uid);long tap=db.queryForObject("SELECT id FROM tap_attempts WHERE user_id=?",Long.class,uid);db.update("INSERT INTO tap_slots(user_id,current_attempt_id) VALUES (?,?)",uid,tap);
        var r=results.report(admin);assertThat(metric(r,"syncSubmitted")).isEqualTo(1);assertThat(r.data().buckets()).anyMatch(b->b.group().equals("SYNC_VERSION_TYPE")&&b.label().equals("INVALID_DEFINITION_OR_TYPE"));assertThat(r.data().buckets()).anyMatch(b->b.group().equals("SYNC_CONFIG")&&b.count()==0);assertThat(r.data().reviewRecords()).anyMatch(review->review.reason().equals("TAP_REVIEW_REQUIRED"));assertThat(r.data().candidates()).isEmpty();
    }

}
