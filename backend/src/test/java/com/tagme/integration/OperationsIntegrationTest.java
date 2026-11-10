package com.tagme.integration;

import com.tagme.auth.*;
import com.tagme.operations.*;
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

@Tag("integration") @Testcontainers @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="tagme.content.worker-enabled=false")
class OperationsIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));r.add("tagme.auth.enabled",()->true);r.add("tagme.auth.token",()->"test-only-token");r.add("tagme.auth.channel-id",()->"c".repeat(26));r.add("tagme.auth.hmac-key",()->Base64.getEncoder().encodeToString(new byte[32]));}
    @Autowired JdbcTemplate db;@Autowired AuthRepository accounts;@Autowired PasswordEncoder passwords;@Autowired ParticipationRepository participation;@Autowired ContentService service;@Autowired ModerationService moderation;@Autowired ObjectMapper json;@Autowired TestRestTemplate http;@Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @MockitoSpyBean ContentRepository repository;@MockitoBean MattermostClient mattermost;@MockitoBean ModerationProvider provider;
    @Autowired ReactionRepository reactions;@Autowired OperationsService operations;@MockitoSpyBean AdminAuditRepository audit;
    AuthPrincipal actor,admin;final Map<String,String> cookies=new HashMap<>();static final String PASSWORD="a long test password";
    @BeforeEach void setup(){
        reset(repository,mattermost,provider,audit);cookies.clear();
        db.update("UPDATE content_items SET approved_revision_id=NULL,pending_revision_id=NULL");
        for(String table:List.of("tap_attempt_events","tap_slots","tap_attempts","sync_results","admin_audit_logs","cheer_likes","wish_comments","moderation_reviews","moderation_jobs","content_revisions","content_items","content_settings","mission_completion_evidence","submission_receipts","event_completions","mission_progress","tags","user_consent_events","user_consents","user_interests","profiles","user_credentials","users","current_consent_policies","consent_policies","event_settings"))db.update("DELETE FROM "+table);
        try(var c=redis.getConnectionFactory().getConnection()){c.serverCommands().flushDb();}
        db.update("UPDATE event_completion_sequence SET next_value=1");db.update("UPDATE missions SET active=TRUE");
        db.update("INSERT INTO event_settings(id,opens_at,submit_closes_at,moderation_closes_at) VALUES (1,UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 2 HOUR)");
        long id=accounts.insertUser("a".repeat(26),"alice");accounts.insertCredential(id,passwords.encode(PASSWORD));actor=accounts.byId(id).orElseThrow().principal();
        db.update("UPDATE users SET mattermost_profile_synced_at=UTC_TIMESTAMP(6),real_name='테스트',region='서울',class_no=1 WHERE id=?",id);db.update("INSERT INTO profiles(user_id,nickname,bio,is_public) VALUES (?,'테스트','테스트 소개',TRUE)",id);db.update("INSERT INTO user_interests SELECT ?,MIN(id) FROM interests",id);
        for(String type:List.of("PARTICIPATION","PROFILE_PUBLICATION")){db.update("INSERT INTO consent_policies VALUES (?,'test-v1','테스트','테스트 전용')",type);db.update("INSERT INTO current_consent_policies VALUES (?,'test-v1')",type);db.update("INSERT INTO user_consents(user_id,policy_type,policy_version,agreed_at) VALUES (?,?,'test-v1',UTC_TIMESTAMP(6))",id,type);}
        db.update("INSERT INTO mission_progress(user_id,mission_id,unlocked_at) SELECT ?,id,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE FROM missions",id);
        long a=accounts.insertUser("z".repeat(26),"admin");accounts.insertCredential(a,accounts.byId(id).orElseThrow().hash());db.update("UPDATE users SET role='ADMIN' WHERE id=?",a);admin=accounts.byId(a).orElseThrow().principal();
        when(mattermost.byUsername("alice")).thenReturn(Optional.of(new MattermostClient.Identity("a".repeat(26),"alice",new MattermostProfile("테스트이름","서울",13,0))));
        when(mattermost.byUsername("admin")).thenReturn(Optional.of(new MattermostClient.Identity("z".repeat(26),"admin",new MattermostProfile("테스트이름","서울",13,0))));
        when(provider.inspect(anyString())).thenAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return approved();});
    }
    ModerationProvider.Decision approved(){return new ModerationProvider.Decision("APPROVED","TEST_ACCEPTED","TEST","test-v1");}
    ContentController.Submit body(String text,Long version){return new ContentController.Submit(text,"PLAIN","ROSE",true,version);}
    OwnSlot submit(Kind kind,String key,String text,Long version){return service.submit(actor,kind,key,body(text,version),version!=null);}
    Job job(){return repository.reviewQueue().getFirst();}
    OwnSlot draft(){return submit(Kind.WISH,"operations-wish-0001","검토 전용 문구",null);}
    void required(){when(provider.inspect(anyString())).thenReturn(new ModerationProvider.Decision("REVIEW_REQUIRED","TEST_REVIEW","TEST","test-v1"));moderation.processOne();}
    void code(Runnable call,String expected){assertThatThrownBy(call::run).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).code()).isEqualTo(expected));}
    @Test void accessUsesCurrentDatabaseRoleRatherThanAnAssertedOrStaleRole() {
        code(()->operations.access(new AuthPrincipal(actor.userId(),actor.version(),"ADMIN")),"ADMIN_REQUIRED");
        code(()->operations.visibility(actor,999999,1,"HIDDEN","검토"),"ADMIN_REQUIRED");
        code(()->moderation.review(actor,999999,new ReviewRequest(0,"APPROVED","검토")),"ADMIN_REQUIRED");
        code(()->operations.task(actor,999999),"ADMIN_REQUIRED");code(()->operations.detail(actor,999999),"ADMIN_REQUIRED");
        assertThat(operations.access(admin).admin()).isTrue();db.update("UPDATE users SET role='USER' WHERE id=?",admin.userId());code(()->operations.tasks(admin,null,null,null),"ADMIN_REQUIRED");
    }
    @Test void taskAndContentCursorsKeepPendingAndApprovedBodiesSeparate()throws Exception {
        var wish=draft();submit(Kind.CHEER,"operations-cheer-001","응원 검토",null);
        var first=operations.tasks(admin,null,1,null);assertThat(first.items()).hasSize(1);assertThat(first.nextCursor()).isNotNull();var next=operations.tasks(admin,first.nextCursor(),1,null);assertThat(next.items()).hasSize(1);assertThat(next.nextCursor()).isNull();assertThat(next.items().getFirst().review().job().id()).isGreaterThan(first.items().getFirst().review().job().id());
        moderation.processOne();var own=service.own(actor.userId(),Kind.WISH);submit(Kind.WISH,"operations-wish-0002","새 비공개 검토본",own.version());
        var row=operations.contents(admin,null,20,"WISH","VISIBLE").items().getFirst();assertThat(row.id()).isEqualTo(wish.id());assertThat(row.approvedRevision().content()).isEqualTo("검토 전용 문구");assertThat(row.pendingRevision().content()).isEqualTo("새 비공개 검토본");
        var encoded=json.writeValueAsString(row);assertThat(encoded).doesNotContain("payloadHash","\"hash\"","password","mattermost");
        code(()->operations.contents(admin,null,51,null,null),"INVALID_FILTER");code(()->operations.tasks(admin,0L,20,null),"INVALID_FILTER");code(()->operations.audits(admin,null,20,"UNKNOWN"),"INVALID_FILTER");
    }
    @Test void hidingAndRestoringRetainApprovalReceiptsAndCompletionEvenAfterClosure() {
        draft();moderation.processOne();var own=service.own(actor.userId(),Kind.WISH);var evidence=db.queryForMap("SELECT * FROM mission_completion_evidence");
        operations.visibility(admin,own.id(),own.version(),"HIDDEN","공개 중지 검토");assertThat(service.feed(Kind.WISH,null).items()).isEmpty();assertThat(service.own(actor.userId(),Kind.WISH).missionCompleted()).isTrue();
        db.update("UPDATE event_settings SET submit_closes_at=UTC_TIMESTAMP(6)-INTERVAL 2 SECOND,moderation_closes_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");
        var hidden=repository.item(own.id(),false);operations.visibility(admin,own.id(),hidden.version(),"VISIBLE","복구 확인");assertThat(service.feed(Kind.WISH,null).items()).hasSize(1);assertThat(db.queryForMap("SELECT * FROM mission_completion_evidence")).isEqualTo(evidence);assertThat(repository.item(own.id(),false).approvedId()).isEqualTo(own.approvedRevision().id());
        assertThat(operations.audits(admin,null,20,null).items()).hasSize(2);
    }
    @Test void hidingFencesInflightWorkAndRestorationNeverAutomaticallyApprovesIt() {
        var draft=draft();var work=moderation.claim();operations.visibility(admin,draft.id(),draft.version(),"HIDDEN","확인 필요");moderation.apply(work,approved());assertThat(service.feed(Kind.WISH,null).items()).isEmpty();assertThat(repository.job(work.job().id(),false).status()).isEqualTo("REVIEW_REQUIRED");
        var hidden=repository.item(draft.id(),false);operations.visibility(admin,draft.id(),hidden.version(),"VISIBLE","공개 복구");moderation.processOne();assertThat(repository.item(draft.id(),false).approvedId()).isNull();assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentVisibilityChangesHaveOneWinnerAndOneAudit()throws Exception {
        var item=draft();try(var pool=Executors.newFixedThreadPool(2)) {var tasks=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{try{operations.visibility(admin,item.id(),item.version(),"HIDDEN","동시 숨김 확인");return true;}catch(AuthException stale){assertThat(stale.code()).isEqualTo("CONTENT_CHANGED");return false;}}));int won=0;for(var task:tasks)if(task.get(30,TimeUnit.SECONDS))won++;assertThat(won).isEqualTo(1);}
        assertThat(operations.audits(admin,null,20,null).items()).hasSize(1);assertThat(repository.item(item.id(),false).version()).isEqualTo(item.version()+1);
    }
    @Test void manualRetryIsBoundedAndPreservesOriginalRevisionAndReceipt() {
        var item=draft();required();var receipt=db.queryForMap("SELECT * FROM submission_receipts");long id=job().id();
        for(int i=0;i<3;i++) {var before=repository.job(id,false);operations.retry(admin,id,before.fence(),"일시 장애 재확인");assertThat(repository.job(id,false).attempts()).isZero();assertThat(repository.job(id,false).fence()).isGreaterThan(before.fence());code(()->operations.retry(admin,id,before.fence(),"중복 요청"),"REVIEW_CHANGED");moderation.processOne();}
        code(()->operations.retry(admin,id,repository.job(id,false).fence(),"한도 초과"),"RETRY_LIMIT");assertThat(db.queryForMap("SELECT * FROM submission_receipts")).isEqualTo(receipt);assertThat(db.queryForObject("SELECT COUNT(*) FROM content_revisions",Integer.class)).isEqualTo(1);assertThat(repository.item(item.id(),false).pendingId()).isEqualTo(item.pendingRevision().id());assertThat(operations.audits(admin,null,20,"MODERATION_RETRY").items()).hasSize(3);
    }
    @Test void retryRejectsHiddenSupersededClosedAndIneligibleWork() {
        var item=draft();required();var j=job();operations.visibility(admin,item.id(),item.version(),"HIDDEN","숨김 확인");code(()->operations.retry(admin,j.id(),repository.job(j.id(),false).fence(),"재검토"),"CONTENT_HIDDEN");
        var hidden=repository.item(item.id(),false);operations.visibility(admin,item.id(),hidden.version(),"VISIBLE","다시 공개");
        db.update("UPDATE profiles SET is_public=FALSE WHERE user_id=?",actor.userId());code(()->operations.retry(admin,j.id(),repository.job(j.id(),false).fence(),"재검토"),"PARTICIPATION_UNAVAILABLE");db.update("UPDATE profiles SET is_public=TRUE WHERE user_id=?",actor.userId());
        var own=service.own(actor.userId(),Kind.WISH);submit(Kind.WISH,"operations-wish-0002","다른 수정본",own.version());code(()->operations.retry(admin,j.id(),repository.job(j.id(),false).fence(),"예전 수정본"),"REVIEW_CHANGED");required();var current=job();db.update("UPDATE event_settings SET moderation_closes_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND,submit_closes_at=UTC_TIMESTAMP(6)-INTERVAL 2 SECOND");code(()->operations.retry(admin,current.id(),current.fence(),"마감 후"),"MODERATION_CLOSED");
    }
    @Test void auditFailureRollsBackVisibilityAndManualApprovalIncludingTheLetter() {
        var item=draft();required();doThrow(new org.springframework.dao.DataAccessResourceFailureException("test audit rollback")).when(audit).record(anyLong(),anyString(),anyString(),anyLong(),anyString(),any(),any());
        assertThatThrownBy(()->operations.visibility(admin,item.id(),item.version(),"HIDDEN","롤백 검사")).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(repository.item(item.id(),false).visibility()).isEqualTo("VISIBLE");
        var retry=job();assertThatThrownBy(()->operations.retry(admin,retry.id(),retry.fence(),"재처리 롤백")).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(repository.job(retry.id(),false).fence()).isEqualTo(retry.fence());assertThat(db.queryForObject("SELECT manual_retry_count FROM moderation_jobs WHERE id=?",Integer.class,retry.id())).isZero();
        var j=job();assertThatThrownBy(()->moderation.review(admin,j.id(),new ReviewRequest(j.fence(),"APPROVED","판정 롤백"))).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(repository.job(j.id(),false).fence()).isEqualTo(j.fence());assertThat(db.queryForObject("SELECT COUNT(*) FROM moderation_reviews WHERE reviewer_id IS NOT NULL",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(1);assertThat(repository.item(item.id(),false).approvedId()).isNull();
    }
    @Test void auditStoresActualDecisionMetadataWithoutContentOrCredentials()throws Exception {
        draft();required();var j=job();db.update("UPDATE profiles SET is_public=FALSE WHERE user_id=?",actor.userId());moderation.review(admin,j.id(),new ReviewRequest(j.fence(),"APPROVED","공개 동의 확인 필요"));
        var log=operations.audits(admin,null,1,"MODERATION_DECISION").items().getFirst();assertThat(log.actorId()).isEqualTo(admin.userId());assertThat(log.after().get("requestedDecision").asText()).isEqualTo("APPROVED");assertThat(log.after().get("status").asText()).isEqualTo("REVIEW_REQUIRED");assertThat(json.writeValueAsString(log)).doesNotContain("검토 전용 문구","password","mattermost","payload_hash");assertThat(service.feed(Kind.WISH,null).items()).isEmpty();
    }
    @Test void hidingParentWishAlsoRemovesApprovedCommentsWithoutDeletingThem() {
        var wish=draft();moderation.processOne();var reaction=new ContentController.Submit("댓글 본문","PLAIN","ROSE",true,null);long child=repository.create(actor.userId(),Kind.COMMENT,null,null);long revision=repository.addRevision(child,1,reaction,"operations-comment-01","b".repeat(64),repository.now());repository.queue(repository.item(child,false),revision);db.update("INSERT INTO wish_comments(wish_id,content_item_id,author_id,idempotency_key,payload_hash) VALUES (?,?,?,?,?)",wish.id(),child,actor.userId(),"operations-comment-01","b".repeat(64));
        moderation.processOne();assertThat(db.queryForObject("SELECT moderation_status FROM content_revisions WHERE id=?",String.class,revision)).isEqualTo("APPROVED");assertThat(reactions.comments(wish.id(),Long.MAX_VALUE).items()).hasSize(1);operations.visibility(admin,wish.id(),repository.item(wish.id(),false).version(),"HIDDEN","원글 확인");assertThat(reactions.comments(wish.id(),Long.MAX_VALUE).items()).isEmpty();assertThat(operations.contents(admin,null,20,"COMMENT",null).items()).hasSize(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM wish_comments",Integer.class)).isEqualTo(1);
    }
    ResponseEntity<Map> raw(HttpMethod method,String path,Object body,String csrf){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(!cookies.isEmpty())h.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));if(csrf!=null)h.add("X-XSRF-TOKEN",csrf);var r=http.exchange("/api/v1"+path,method,new HttpEntity<>(body,h),Map.class);for(String c:r.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)){var p=c.split(";",2)[0].split("=",2);if(p.length==2)cookies.put(p[0],p[1]);}return r;}
    ResponseEntity<Map> send(String path,Object body){var c=raw(HttpMethod.GET,"/csrf",null,null);return raw(HttpMethod.POST,path,body,(String)c.getBody().get("token"));}
    @Test void httpChecksSessionCsrfRoleAndRequiredFenceAndMarksPrivateResponsesNoStore() {
        assertThat(raw(HttpMethod.GET,"/admin/access",null,null).getStatusCode().value()).isEqualTo(401);assertThat(send("/auth/login",Map.of("username","alice","password",PASSWORD)).getStatusCode().value()).isEqualTo(200);assertThat(raw(HttpMethod.GET,"/admin/moderation/tasks",null,null).getStatusCode().value()).isEqualTo(403);cookies.clear();assertThat(send("/auth/login",Map.of("username","admin","password",PASSWORD)).getStatusCode().value()).isEqualTo(200);
        draft();required();var j=job();assertThat(raw(HttpMethod.GET,"/admin/content",null,null).getHeaders().getCacheControl()).contains("no-store");assertThat(raw(HttpMethod.POST,"/admin/moderation/"+j.id()+"/retry",Map.of("expectedFence",j.fence(),"reason","재검토"),null).getStatusCode().value()).isEqualTo(403);
        assertThat(send("/admin/moderation/"+j.id()+"/retry",Map.of("reason","누락 검사")).getStatusCode().value()).isEqualTo(400);assertThat(send("/admin/moderation/"+j.id()+"/decision",Map.of("decision","APPROVED","reason","누락 검사")).getStatusCode().value()).isEqualTo(400);
        var c=raw(HttpMethod.GET,"/csrf",null,null);assertThat(raw(HttpMethod.PATCH,"/admin/content/"+j.itemId()+"/visibility",Map.of("expectedVersion",repository.item(j.itemId(),false).version(),"visibility","HIDDEN","reason","운영 확인"),(String)c.getBody().get("token")).getStatusCode().value()).isEqualTo(204);
    }
    // Award fixtures exist only in this class's isolated Testcontainers; no operation writes awards.
    long awardInsert(String sql,Object...args) {
        var keys=new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(connection->{var statement=connection.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    java.time.Instant awardOpen(){return db.queryForObject("SELECT opens_at FROM event_settings WHERE id=1",java.sql.Timestamp.class).toInstant();}
    java.sql.Timestamp stamp(java.time.Instant value){return java.sql.Timestamp.from(value);}
    long awardItem(long author,String kind,java.time.Instant submitted) {
        long item=awardInsert("INSERT INTO content_items(author_id,kind) VALUES (?,?)",author,kind);
        long revision=awardInsert("INSERT INTO content_revisions(item_id,revision_no,content_text,template_id,style,anonymous,submitted_at,moderation_status,idempotency_key,payload_hash) VALUES (?,1,'AWARD TEST ONLY','PLAIN','ROSE',TRUE,?,'APPROVED',?,?)",item,stamp(submitted),UUID.randomUUID().toString(),"e".repeat(64));
        db.update("INSERT INTO moderation_jobs(revision_id,status) VALUES (?,'APPROVED')",revision);
        db.update("UPDATE content_items SET approved_revision_id=? WHERE id=?",revision,item);return item;
    }
    long awardRevision(long item){return db.queryForObject("SELECT approved_revision_id FROM content_items WHERE id=?",Long.class,item);}
    long awardUser(String name,int finishSeconds,int registeredSeconds) {
        long user=accounts.insertUser("award-test-"+name,"award-test-"+name);accounts.insertCredential(user,accounts.byId(actor.userId()).orElseThrow().hash());
        accounts.syncProfile(user,new MattermostProfile("테스트","서울",1,0));
        var opened=awardOpen();var finish=opened.plusSeconds(finishSeconds);
        db.update("INSERT INTO profiles(user_id,nickname,bio,is_public,published_at) VALUES (?,?,'테스트',TRUE,?)",user,name,stamp(opened.plusSeconds(registeredSeconds)));
        db.update("INSERT INTO user_interests SELECT ?,MIN(id) FROM interests",user);
        for(String type:List.of("PARTICIPATION","PROFILE_PUBLICATION"))db.update("INSERT INTO user_consents(user_id,policy_type,policy_version,agreed_at) VALUES (?,?,'test-v1',?)",user,type,stamp(opened));
        db.update("INSERT IGNORE INTO sync_questionnaires(version,definition_json) VALUES ('award-test-v1','{}')");
        db.update("INSERT INTO sync_results(user_id,questionnaire_version,answers_json,axis_scores_json,type_code,payload_hash,submitted_at) VALUES (?,'award-test-v1','[]','{}','FIXTURE',?,?)",user,"e".repeat(64),stamp(finish.minusSeconds(3)));
        long wish=awardItem(user,"WISH",finish.minusSeconds(2)),cheer=awardItem(user,"CHEER",finish);
        long tap=awardInsert("INSERT INTO tap_attempts(user_id,generation,idempotency_key,starts_at,ends_at,submit_deadline,status,tap_count,submitted_at,payload_hash) VALUES (?,1,?,?,?,?,'VALID',120,?,?)",user,UUID.randomUUID().toString(),stamp(finish.minusSeconds(16)),stamp(finish.minusSeconds(6)),stamp(finish.plusSeconds(4)),stamp(finish.minusSeconds(1)),"e".repeat(64));
        db.update("INSERT INTO tap_slots(user_id,current_attempt_id) VALUES (?,?)",user,tap);
        var events=new ArrayList<Object[]>();for(int ms=0;ms<9600;ms+=80)events.add(new Object[]{tap,ms});
        db.batchUpdate("INSERT INTO tap_attempt_events(attempt_id,event_type,relative_time_ms) VALUES (?,'TAP',?)",events);
        var codes=List.of("SHOW_ME","SYNC_ME","MAKE_A_WISH","FAST_TAP","SAY_SOMETHING");var kinds=List.of("PROFILE","SYNC_RESULT","WISH_REVISION","GAME_ATTEMPT","CHEER_REVISION");
        var sources=List.of(1L,user,awardRevision(wish),tap,awardRevision(cheer));
        for(int i=0;i<codes.size();i++) {
            long mission=db.queryForObject("SELECT id FROM missions WHERE code=?",Long.class,codes.get(i));
            db.update("INSERT INTO mission_progress(user_id,mission_id,status,unlocked_at,completed_at) VALUES (?,?,'COMPLETED',?,UTC_TIMESTAMP(6))",user,mission,stamp(opened));
            var receipt=participation.receipt(user,kinds.get(i),sources.get(i),finish.minusSeconds(4-i));
            db.update("INSERT INTO mission_completion_evidence(user_id,mission_id,source_kind,source_id,receipt_order,accepted_received_at) VALUES (?,?,?,?,?,?)",user,mission,kinds.get(i),sources.get(i),receipt.id(),stamp(finish.minusSeconds(4-i)));
        }
        long order=db.queryForObject("SELECT COALESCE(MAX(completion_order),0)+1 FROM event_completions",Long.class);
        db.update("INSERT INTO event_completions(user_id,completion_order,completed_at) VALUES (?,?,UTC_TIMESTAMP(6))",user,order);return user;
    }
    long awardContent(long author,String kind){return db.queryForObject("SELECT id FROM content_items WHERE author_id=? AND kind=?",Long.class,author,kind);}
    long awardComment(long author,long wish) {
        var submitted=awardOpen().plusSeconds(200);long item=awardItem(author,"COMMENT",submitted);
        db.update("INSERT INTO wish_comments(wish_id,content_item_id,author_id,idempotency_key,payload_hash) VALUES (?,?,?,?,?)",wish,item,author,UUID.randomUUID().toString(),"e".repeat(64));
        participation.receipt(author,"COMMENT_REVISION",awardRevision(item),submitted);return item;
    }
    AwardModels.Report awardReport(String category){return operations.awards(admin,category,0,20);}
    @Test void awardsUseReceiptTimeProfileTiesAndCurrentPublicApprovalRatherThanCompletionOrder() {
        long a=awardUser("award-a",100,2),b=awardUser("award-b",90,1);
        db.update("UPDATE mission_progress SET completed_at=UTC_TIMESTAMP(6)-INTERVAL 30 MINUTE WHERE user_id=?",a);
        db.update("UPDATE mission_progress SET completed_at=UTC_TIMESTAMP(6)-INTERVAL 10 MINUTE WHERE user_id=?",b);
        assertThat(awardReport("FIRST_COMPLETION").items()).extracting(AwardModels.Candidate::userId).containsExactly(b,a);
        assertThat(db.queryForObject("SELECT completion_order FROM event_completions WHERE user_id=?",Long.class,a)).isEqualTo(1);
        var before=awardReport("FIRST_COMPLETION").items();
        db.update("UPDATE mission_progress SET completed_at=completed_at+INTERVAL 5 MINUTE WHERE user_id=?",b);
        assertThat(awardReport("FIRST_COMPLETION").items()).isEqualTo(before);
        // Current approved revision may change while the earliest valid evidence remains immutable.
        long cheer=awardContent(a,"CHEER");long old=awardRevision(cheer);
        long newer=awardInsert("INSERT INTO content_revisions(item_id,revision_no,content_text,template_id,style,anonymous,submitted_at,moderation_status,idempotency_key,payload_hash) VALUES (?,2,'NEW TEST ONLY','PLAIN','ROSE',TRUE,?,'QUEUED',?,?)",cheer,stamp(awardOpen().plusSeconds(300)),UUID.randomUUID().toString(),"f".repeat(64));
        db.update("INSERT INTO moderation_jobs(revision_id) VALUES (?)",newer);db.update("UPDATE content_items SET pending_revision_id=? WHERE id=?",newer,cheer);
        assertThat(awardRevision(cheer)).isEqualTo(old);assertThat(awardReport("FIRST_COMPLETION").items()).isEqualTo(before);
        db.update("UPDATE profiles SET bio='수정된 테스트 소개',version=version+1 WHERE user_id=?",a);
        db.update("UPDATE content_revisions SET moderation_status='APPROVED' WHERE id=?",newer);db.update("UPDATE moderation_jobs SET status='APPROVED' WHERE revision_id=?",newer);
        db.update("UPDATE content_items SET approved_revision_id=?,pending_revision_id=NULL WHERE id=?",newer,cheer);
        assertThat(awardReport("FIRST_COMPLETION").items()).isEqualTo(before);
        db.update("UPDATE content_items SET visibility_status='HIDDEN' WHERE id=?",cheer);
        assertThat(awardReport("FIRST_COMPLETION").items()).extracting(AwardModels.Candidate::userId).containsExactly(b);
    }
    @Test void awardsCountDistinctOtherWishesAndActualUncancelledPublicHearts() {
        long a=awardUser("award-a",100,2),b=awardUser("award-b",90,1),c=awardUser("award-c",80,3);
        long wish=awardContent(b,"WISH"),other=awardContent(c,"WISH"),cheer=awardContent(a,"CHEER");
        awardComment(a,wish);awardComment(a,wish);awardComment(a,awardContent(a,"WISH"));long hidden=awardComment(a,other);
        db.update("UPDATE content_items SET visibility_status='HIDDEN' WHERE id=?",hidden);
        assertThat(awardReport("COMMENT_KING").items().getFirst().score()).isEqualTo(1);
        db.update("INSERT INTO cheer_likes(card_id,user_id,created_at) VALUES (?,?,?),(?,?,?),(?,?,?)",cheer,b,stamp(awardOpen().plusSeconds(200)),cheer,c,stamp(awardOpen().plusSeconds(200)),cheer,a,stamp(awardOpen().plusSeconds(200)));
        assertThatThrownBy(()->db.update("INSERT INTO cheer_likes(card_id,user_id) VALUES (?,?)",cheer,b)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(awardReport("POPULAR_CHEER").items().getFirst().score()).isEqualTo(2);
        db.update("DELETE FROM cheer_likes WHERE card_id=? AND user_id=?",cheer,c);
        assertThat(awardReport("POPULAR_CHEER").items().getFirst().score()).isEqualTo(1);
        db.update("UPDATE profiles SET published_at=NULL WHERE user_id=?",b);
        assertThat(awardReport("POPULAR_CHEER").items().stream().filter(row->row.userId()==a).findFirst().orElseThrow().score()).isEqualTo(1);
        db.update("UPDATE profiles SET is_public=FALSE WHERE user_id=?",b);
        assertThat(awardReport("POPULAR_CHEER").items().stream().filter(row->row.userId()==a).findFirst().orElseThrow().score()).isZero();
        assertThat(awardReport("COMMENT_KING").items().stream().filter(row->row.userId()==a).findFirst().orElseThrow().score()).isZero();
    }
    @Test void awardTapReviewCorruptProofExactTiesAndRoleBypassAreNeverSilentlyPromoted() {
        long a=awardUser("award-a",100,1),b=awardUser("award-b",100,1);
        assertThat(awardReport("FIRST_COMPLETION").items()).allMatch(row->row.rank()==1&&row.unresolvedTie());
        assertThat(awardReport("FIRST_COMPLETION").items().getFirst().overlappingTopCategories()).hasSize(4);
        db.update("UPDATE tap_attempts SET status='REVIEW_REQUIRED',tap_count=500,review_reason='UNUSUAL_RATE' WHERE user_id=?",a);
        var report=awardReport("FAST_TAP");assertThat(report.items()).extracting(AwardModels.Candidate::userId).containsExactly(b);assertThat(report.reviewTotal()).isEqualTo(1);
        db.update("UPDATE tap_attempts SET status='INTERRUPTED' WHERE user_id=?",b);assertThat(awardReport("FAST_TAP").items()).isEmpty();
        db.update("UPDATE tap_attempts SET status='VALID',tap_count=201 WHERE user_id=?",b);assertThat(awardReport("FAST_TAP").items()).isEmpty();
        db.update("UPDATE tap_attempts SET tap_count=120 WHERE user_id=?",b);
        db.update("UPDATE mission_completion_evidence SET accepted_received_at=accepted_received_at-INTERVAL 1 DAY WHERE user_id=? AND source_kind='PROFILE'",b);
        assertThat(awardReport("FIRST_COMPLETION").items()).isEmpty();
        code(()->operations.awards(new AuthPrincipal(actor.userId(),actor.version(),"ADMIN"),"FIRST_COMPLETION",0,20),"ADMIN_REQUIRED");
        code(()->operations.awards(admin,"RANDOM",0,20),"INVALID_FILTER");
        assertThat(raw(HttpMethod.GET,"/admin/awards",null,null).getStatusCode().value()).isEqualTo(401);
        send("/auth/login",Map.of("username","alice","password",PASSWORD));assertThat(raw(HttpMethod.GET,"/admin/awards",null,null).getStatusCode().value()).isEqualTo(403);
        cookies.clear();send("/auth/login",Map.of("username","admin","password",PASSWORD));
        var response=raw(HttpMethod.GET,"/admin/awards",null,null);assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody().toString()).doesNotContain("AWARD TEST ONLY","payload_hash","password","mattermost");
    }
    @Test void awardsRepeatWithinOneSnapshotWhileVisibilityChangesCommitAndNextReadReflectsChange() throws Exception {
        long a=awardUser("award-a",100,1);long wish=awardContent(a,"WISH");
        var snapshot=new TransactionTemplate(manager);snapshot.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.execute(status->{var first=awardReport("FIRST_COMPLETION");var pool=Executors.newSingleThreadExecutor();
            try{pool.submit(()->db.update("UPDATE content_items SET visibility_status='HIDDEN' WHERE id=?",wish)).get(10,TimeUnit.SECONDS);}catch(Exception failure){throw new RuntimeException(failure);}finally{pool.shutdownNow();}
            assertThat(awardReport("FIRST_COMPLETION").items()).isEqualTo(first.items());return null;});
        assertThat(awardReport("FIRST_COMPLETION").items()).isEmpty();
    }

}
