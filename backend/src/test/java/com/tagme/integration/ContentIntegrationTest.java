package com.tagme.integration;

import com.tagme.auth.*;
import com.tagme.content.*;
import com.tagme.participation.*;
import com.tagme.content.ContentModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration") @Testcontainers @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"tagme.content.worker-enabled=false","tagme.moderation.enabled=true"})
class ContentIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));r.add("tagme.auth.enabled",()->true);r.add("tagme.auth.token",()->"test-only-token");r.add("tagme.auth.channel-id",()->"c".repeat(26));r.add("tagme.auth.hmac-key",()->Base64.getEncoder().encodeToString(new byte[32]));}
    @Autowired JdbcTemplate db;@Autowired AuthRepository accounts;@Autowired PasswordEncoder passwords;@Autowired ParticipationRepository participation;@Autowired ContentService service;@Autowired ModerationService moderation;@Autowired ObjectMapper json;@Autowired TestRestTemplate http;@Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @MockitoSpyBean ContentRepository repository;@MockitoBean MattermostClient mattermost;@MockitoBean ModerationProvider provider;
    AuthPrincipal actor,admin;final Map<String,String> cookies=new HashMap<>();static final String PASSWORD="a long test password";
    @BeforeEach void setup(){
        reset(repository,mattermost,provider);cookies.clear();
        db.update("UPDATE content_items SET approved_revision_id=NULL,pending_revision_id=NULL");
        for(String table:List.of("admin_audit_logs","moderation_reviews","moderation_jobs","content_revisions","content_items","content_settings","mission_completion_evidence","submission_receipts","event_completions","mission_progress","tags","user_consent_events","user_consents","user_interests","profiles","user_credentials","users","current_consent_policies","consent_policies","event_settings"))db.update("DELETE FROM "+table);
        try(var c=redis.getConnectionFactory().getConnection()){c.serverCommands().flushDb();}
        db.update("UPDATE event_completion_sequence SET next_value=1");db.update("UPDATE missions SET active=TRUE");
        db.update("INSERT INTO event_settings(id,opens_at,submit_closes_at,moderation_closes_at) VALUES (1,UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 2 HOUR)");
        long id=accounts.insertUser("a".repeat(26),"alice");accounts.insertCredential(id,passwords.encode(PASSWORD));actor=accounts.byId(id).orElseThrow().principal();
        db.update("UPDATE users SET mattermost_profile_synced_at=UTC_TIMESTAMP(6),real_name='테스트',region='서울',class_no=1 WHERE id=?",id);db.update("INSERT INTO profiles(user_id,nickname,bio,is_public) VALUES (?,'테스트','테스트 소개',TRUE)",id);db.update("INSERT INTO user_interests SELECT ?,MIN(id) FROM interests WHERE active=TRUE GROUP BY category",id);
        for(String type:List.of("PARTICIPATION","PROFILE_PUBLICATION")){db.update("INSERT INTO consent_policies VALUES (?,'test-v1','테스트','테스트 전용')",type);db.update("INSERT INTO current_consent_policies VALUES (?,'test-v1')",type);db.update("INSERT INTO user_consents(user_id,policy_type,policy_version,agreed_at) VALUES (?,?,'test-v1',UTC_TIMESTAMP(6))",id,type);}
        db.update("INSERT INTO mission_progress(user_id,mission_id,unlocked_at) SELECT ?,id,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE FROM missions",id);
        long a=accounts.insertUser("z".repeat(26),"admin");accounts.insertCredential(a,accounts.byId(id).orElseThrow().hash());db.update("UPDATE users SET role='ADMIN' WHERE id=?",a);admin=accounts.byId(a).orElseThrow().principal();
        when(mattermost.byUsername("alice")).thenReturn(Optional.of(new MattermostClient.Identity("a".repeat(26),"alice",new MattermostProfile("테스트이름","서울",13,0))));
        when(provider.inspect(anyString())).thenAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return approved();});
    }
    ModerationProvider.Decision approved(){return new ModerationProvider.Decision("APPROVED","TEST_ACCEPTED","TEST","test-v1");}
    ContentController.Submit body(String text,Long version){return new ContentController.Submit(text,"PLAIN","ROSE",true,version);}
    OwnSlot submit(Kind kind,String key,String text,Long version){return service.submit(actor,kind,key,body(text,version),version!=null);}
    @Test void submissionGrantsLetterWhilePublicationAndLuckWaitForApproval(){
        db.update("INSERT INTO content_settings VALUES (1,10,20,3)");var slot=submit(Kind.WISH,"first-wish-key-0001","테스트 소원",null);assertThat(slot.missionCompleted()).isTrue();assertThat(slot.luckScore()).isNull();assertThat(service.feed(Kind.WISH,null).items()).isEmpty();
        var receipt=db.queryForMap("SELECT * FROM submission_receipts");moderation.processOne();var approved=service.own(actor.userId(),Kind.WISH);
        assertThat(approved.missionCompleted()).isTrue();assertThat(approved.luckScore()).isBetween(10,20);assertThat(approved.landingZone()).isBetween(0,2);assertThat(service.feed(Kind.WISH,null).items()).hasSize(1);
        assertThat(db.queryForMap("SELECT accepted_received_at,receipt_order FROM mission_completion_evidence")).containsEntry("receipt_order",receipt.get("id")).containsEntry("accepted_received_at",receipt.get("received_at"));
        assertThat(service.submit(actor,Kind.WISH,"first-wish-key-0001",body("테스트 소원",null),false).id()).isEqualTo(slot.id());
        assertThatThrownBy(()->submit(Kind.WISH,"first-wish-key-0001","다른 내용",null)).isInstanceOf(AuthException.class);
    }
    @Test void concurrentFirstRequestsHaveOnlyOneSlotRevisionJobAndReceipt()throws Exception{
        try(var pool=Executors.newFixedThreadPool(6)){List<Future<OwnSlot>> tasks=new ArrayList<>();for(int i=0;i<16;i++)tasks.add(pool.submit(()->submit(Kind.WISH,"concurrent-wish-001","테스트",null)));Set<Long> ids=new HashSet<>();for(var t:tasks)ids.add(t.get(30,TimeUnit.SECONDS).id());assertThat(ids).hasSize(1);}
        for(String table:List.of("content_items","content_revisions","moderation_jobs","submission_receipts"))assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->submit(Kind.WISH,"different-key-0001","테스트",null)).isInstanceOf(AuthException.class);
    }
    @Test void staleAndDuplicateWorkerResponsesCannotOverwriteTheNewRevisionOrGrantTwice(){
        var first=submit(Kind.CHEER,"cheer-version-key01","테스트 응원",null);var old=moderation.claim();var second=submit(Kind.CHEER,"cheer-version-key02","테스트 수정",first.version());moderation.apply(old,approved());assertThat(service.feed(Kind.CHEER,null).items()).isEmpty();
        var work=moderation.claim();moderation.apply(work,approved());moderation.apply(work,approved());assertThat(service.feed(Kind.CHEER,null).items().getFirst().content()).isEqualTo("테스트 수정");assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM moderation_reviews",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->submit(Kind.CHEER,"cheer-version-key03","오래된 수정",second.version())).isInstanceOf(AuthException.class);
    }
    @Test void rejectingAnEditKeepsApprovedTextFirstCompletionAndLuck(){
        db.update("INSERT INTO content_settings VALUES (1,10,20,3)");submit(Kind.WISH,"accepted-wish-key01","승인본",null);moderation.processOne();var old=service.own(actor.userId(),Kind.WISH);var evidence=db.queryForMap("SELECT * FROM mission_completion_evidence");
        submit(Kind.WISH,"accepted-wish-key02","새 수정본",old.version());assertThat(service.feed(Kind.WISH,null).items().getFirst().content()).isEqualTo("승인본");
        var work=moderation.claim();moderation.apply(work,new ModerationProvider.Decision("REJECTED","TEST_REJECTED","TEST","test-v1"));var next=service.own(actor.userId(),Kind.WISH);assertThat(next.approvedRevision().content()).isEqualTo("승인본");assertThat(next.latestRevision().status()).isEqualTo("REJECTED");assertThat(next.luckScore()).isEqualTo(old.luckScore());assertThat(db.queryForMap("SELECT * FROM mission_completion_evidence")).isEqualTo(evidence);
    }
    @Test void expiredLeasesAreReclaimedAndOldFenceCannotPublish(){submit(Kind.WISH,"lease-wish-key0001","테스트",null);var old=moderation.claim();db.update("UPDATE moderation_jobs SET lease_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");var next=moderation.claim();assertThat(next.job().fence()).isGreaterThan(old.job().fence());moderation.apply(old,approved());assertThat(service.feed(Kind.WISH,null).items()).isEmpty();moderation.apply(next,approved());assertThat(service.feed(Kind.WISH,null).items()).hasSize(1);}
    @Test void temporaryFailuresBackOffThenRequireReviewWithoutPublishing(){submit(Kind.WISH,"failure-wish-key001","테스트",null);when(provider.inspect(anyString())).thenThrow(new ModerationProvider.TemporaryFailure());for(int i=0;i<3;i++){moderation.processOne();db.update("UPDATE moderation_jobs SET next_run_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");}assertThat(service.own(actor.userId(),Kind.WISH).pendingRevision().status()).isEqualTo("REVIEW_REQUIRED");assertThat(db.queryForObject("SELECT attempts FROM moderation_jobs",Integer.class)).isEqualTo(3);assertThat(service.feed(Kind.WISH,null).items()).isEmpty();assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);}
    @Test void missingProviderContactDetailsAndInvalidProviderResponsesNeverApprove(){submit(Kind.WISH,"contact-wish-key001","010-1234-5678",null);moderation.processOne();verifyNoInteractions(provider);assertThat(service.own(actor.userId(),Kind.WISH).pendingRevision().reason()).isEqualTo("CONTACT_OR_URL");var state=service.own(actor.userId(),Kind.WISH);submit(Kind.WISH,"contact-wish-key002","정상 테스트",state.version());when(provider.inspect(anyString())).thenReturn(null);moderation.processOne();assertThat(service.own(actor.userId(),Kind.WISH).pendingRevision().reason()).isEqualTo("INVALID_PROVIDER_RESPONSE");}
    @Test void submissionAndApprovalRollbackAllPointersJobsReceiptsAndCompletion(){
        doAnswer(i->{i.callRealMethod();throw new org.springframework.dao.DataAccessResourceFailureException("test-only rollback");}).when(repository).queue(any(),anyLong());assertThatThrownBy(()->submit(Kind.WISH,"rollback-wish-key01","테스트",null)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM content_items",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM submission_receipts",Integer.class)).isZero();doCallRealMethod().when(repository).queue(any(),anyLong());
        submit(Kind.WISH,"rollback-wish-key02","테스트",null);var work=moderation.claim();doAnswer(i->{i.callRealMethod();throw new org.springframework.dao.DataAccessResourceFailureException("test-only rollback");}).when(repository).decision(any(),anyString(),anyString(),anyString(),anyString(),anyString(),nullable(Long.class));
        assertThatThrownBy(()->moderation.apply(work,approved())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(service.feed(Kind.WISH,null).items()).isEmpty();assertThat(db.queryForObject("SELECT COUNT(*) FROM moderation_reviews",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);
    }
    @Test void beforeDeadlineReceiptCanPublishAfterSubmissionClose(){submit(Kind.WISH,"cutoff-wish-key001","테스트",null);db.update("UPDATE event_settings SET submit_closes_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");db.update("UPDATE content_revisions SET submitted_at=UTC_TIMESTAMP(6)-INTERVAL 5 SECOND");db.update("UPDATE submission_receipts SET received_at=UTC_TIMESTAMP(6)-INTERVAL 5 SECOND");moderation.processOne();assertThat(service.feed(Kind.WISH,null).items()).hasSize(1);}
    @Test void privacyWithdrawalAndModerationClosureKeepContentOutOfPublicFeed(){submit(Kind.CHEER,"private-cheer-key01","테스트",null);db.update("UPDATE profiles SET is_public=FALSE WHERE user_id=?",actor.userId());moderation.processOne();assertThat(service.feed(Kind.CHEER,null).items()).isEmpty();assertThat(service.own(actor.userId(),Kind.CHEER).pendingRevision().status()).isEqualTo("REVIEW_REQUIRED");db.update("UPDATE profiles SET is_public=TRUE WHERE user_id=?",actor.userId());db.update("UPDATE event_settings SET submit_closes_at=UTC_TIMESTAMP(6)-INTERVAL 2 SECOND,moderation_closes_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");var job=repository.reviewQueue().getFirst();assertThatThrownBy(()->moderation.review(admin,job.id(),new ReviewRequest(job.fence(),"APPROVED","테스트 사유"))).isInstanceOf(AuthException.class);}
    @Test void anonymousPublicJsonHasNoAuthorIdsAndHiddenOrNonPublicAuthorsAreFiltered()throws Exception{submit(Kind.CHEER,"anonymous-cheer-001","테스트",null);moderation.processOne();String response=json.writeValueAsString(service.feed(Kind.CHEER,null));assertThat(response).doesNotContain("authorId","userId","revisionId","submittedAt","hash","테스트 소개");assertThat(service.feed(Kind.CHEER,null).items().getFirst().nickname()).isNull();db.update("UPDATE user_consents SET withdrawn_at=UTC_TIMESTAMP(6) WHERE user_id=?",actor.userId());assertThat(service.feed(Kind.CHEER,null).items()).isEmpty();}
    @Test void adminDecisionsAreVersionedAuditedAndBlockLateWorkerResponses(){submit(Kind.WISH,"admin-review-key001","테스트",null);var work=moderation.claim();var job=work.job();assertThatThrownBy(()->moderation.review(actor,job.id(),new ReviewRequest(job.fence(),"APPROVED","테스트 사유"))).isInstanceOf(AuthException.class);moderation.review(admin,job.id(),new ReviewRequest(job.fence(),"REJECTED","검토 사유"));moderation.apply(work,approved());assertThat(service.feed(Kind.WISH,null).items()).isEmpty();assertThat(db.queryForObject("SELECT reason FROM moderation_reviews",String.class)).isEqualTo("검토 사유");assertThatThrownBy(()->moderation.review(admin,job.id(),new ReviewRequest(job.fence(),"APPROVED","중복 판정"))).isInstanceOf(AuthException.class);}
    @Test void concurrentClaimsOnlyLeaseOneJob() throws Exception {
        submit(Kind.WISH,"parallel-claim-key01","테스트",null);
        try(var pool=Executors.newFixedThreadPool(6)) {
            List<Future<Work>> tasks=new ArrayList<>();for(int i=0;i<12;i++)tasks.add(pool.submit(moderation::claim));
            int claimed=0;for(var task:tasks)if(task.get(30,TimeUnit.SECONDS)!=null)claimed++;assertThat(claimed).isEqualTo(1);
        }
        assertThat(db.queryForObject("SELECT attempts FROM moderation_jobs",Integer.class)).isEqualTo(1);
    }
    @Test void humanApprovalOfUnconfiguredProviderKeepsTheSubmissionReceipt() throws Exception {
        submit(Kind.WISH,"human-approve-key01","테스트",null);
        when(provider.inspect(anyString())).thenReturn(new ModerationProvider.Decision("REVIEW_REQUIRED","PROVIDER_NOT_CONFIGURED","NONE","unconfigured"));moderation.processOne();
        var task=moderation.queue(admin).getFirst();assertThat(task.revision().content()).isEqualTo("테스트");
        moderation.review(admin,task.job().id(),new ReviewRequest(task.job().fence(),"APPROVED","직접 확인한 테스트 사유"));
        assertThat(service.own(actor.userId(),Kind.WISH).missionCompleted()).isTrue();assertThat(service.feed(Kind.WISH,null).items()).hasSize(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM moderation_reviews",Integer.class)).isEqualTo(2);
        assertThat(json.writeValueAsString(service.own(actor.userId(),Kind.WISH))).doesNotContain("hash");
    }
    @Test void lastContentSubmissionsAllocateOneOperationalCompletionEvenWhenPublicationIsConcurrent() throws Exception {
        db.update("UPDATE mission_progress p JOIN missions m ON m.id=p.mission_id SET p.status='COMPLETED',p.completed_at=UTC_TIMESTAMP(6) WHERE p.user_id=? AND m.code IN ('SHOW_ME','SYNC_ME','FAST_TAP')",actor.userId());
        submit(Kind.WISH,"last-wish-key-0001","테스트 소원",null);submit(Kind.CHEER,"last-cheer-key-001","테스트 응원",null);
        var first=moderation.claim();var second=moderation.claim();
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->moderation.apply(first,approved()));var b=pool.submit(()->moderation.apply(second,approved()));a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM event_completions",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT next_value FROM event_completion_sequence",Long.class)).isEqualTo(2);
    }
    @Test void profileLinksSwitchOnlyWhenTheAnonymityRevisionIsApproved() {
        submit(Kind.CHEER,"link-cheer-key-0001","테스트",null);moderation.processOne();assertThat(service.feed(Kind.CHEER,null).items().getFirst().profileId()).isNull();
        var own=service.own(actor.userId(),Kind.CHEER);service.submit(actor,Kind.CHEER,"link-cheer-key-0002",new ContentController.Submit("공개 테스트","NOTE","INK",false,own.version()),true);
        assertThat(service.feed(Kind.CHEER,null).items().getFirst().profileId()).isNull();moderation.processOne();assertThat(service.feed(Kind.CHEER,null).items().getFirst().profileId()).isEqualTo(actor.userId());
        var updated=service.own(actor.userId(),Kind.CHEER);service.submit(actor,Kind.CHEER,"link-cheer-key-0003",new ContentController.Submit("익명 테스트","PLAIN","ROSE",true,updated.version()),true);moderation.processOne();assertThat(service.feed(Kind.CHEER,null).items().getFirst().profileId()).isNull();
    }
    ResponseEntity<Map> raw(HttpMethod method,String path,Object body,String csrf,String key){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(!cookies.isEmpty())h.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));if(csrf!=null)h.add("X-XSRF-TOKEN",csrf);if(key!=null)h.add("Idempotency-Key",key);var r=http.exchange("/api/v1"+path,method,new HttpEntity<>(body,h),Map.class);for(String c:r.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)){var p=c.split(";",2)[0].split("=",2);if(p.length==2)cookies.put(p[0],p[1]);}return r;}
    ResponseEntity<Map> send(String path,Object body){var c=raw(HttpMethod.GET,"/csrf",null,null,null);return raw(HttpMethod.POST,path,body,(String)c.getBody().get("token"),"http-test-key-0001");}
    @Test void httpAcceptedResponseGrantsLetterAndContainsPrivateState(){
        assertThat(send("/auth/login",Map.of("username","alice","password",PASSWORD)).getStatusCode().value()).isEqualTo(200);
        var payload=Map.of("content","테스트 소원","templateId","PLAIN","style","ROSE","anonymous",true);
        var first=send("/wishes",payload);assertThat(first.getStatusCode().value()).isEqualTo(202);assertThat(first.getBody().get("missionCompleted")).isEqualTo(true);
        assertThat(((Map<?,?>)first.getBody().get("pendingRevision")).containsKey("hash")).isFalse();
        assertThat(send("/wishes",payload).getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(raw(HttpMethod.GET,"/wishes/me",null,null,null).getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);
        var invalid=new HashMap<String,Object>(payload);invalid.put("style","CUSTOM_CSS");assertThat(send("/cheers",invalid).getStatusCode().value()).isEqualTo(400);
    }
    @Test void httpRequiresSessionCsrfAndUnlockAndRejectsClientApproval(){var payload=Map.of("content","테스트","templateId","PLAIN","style","ROSE","anonymous",true);assertThat(send("/wishes",payload).getStatusCode().value()).isEqualTo(401);assertThat(send("/auth/login",Map.of("username","alice","password",PASSWORD)).getStatusCode().value()).isEqualTo(200);assertThat(raw(HttpMethod.POST,"/wishes",payload,null,"http-test-key-0001").getStatusCode().value()).isEqualTo(403);var extra=new HashMap<String,Object>(payload);extra.put("moderationStatus","APPROVED");assertThat(send("/wishes",extra).getStatusCode().value()).isEqualTo(400);assertThat(send("/admin/moderation/1/decision",Map.of("expectedFence",0,"decision","APPROVED","reason","테스트")).getStatusCode().value()).isEqualTo(403);db.update("DELETE FROM mission_progress WHERE user_id=? AND mission_id=(SELECT id FROM missions WHERE code='MAKE_A_WISH')",actor.userId());assertThat(send("/wishes",payload).getStatusCode().value()).isEqualTo(403);}
}
