package com.tagme.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.auth.*;
import com.tagme.content.*;
import com.tagme.operations.*;
import com.tagme.participation.*;
import com.tagme.results.*;
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
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static com.tagme.content.ContentModels.Kind.*;
import static com.tagme.participation.ParticipationModels.MissionCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration") @Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"tagme.auth.enabled=false","tagme.admin.bootstrap-id=admin:reset-test","tagme.admin.bootstrap-password=integration reset operator password","tagme.content.worker-enabled=false"})
class DirectPublicationResetIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));r.add("tagme.auth.hmac-key",()->Base64.getEncoder().encodeToString(new byte[32]));r.add("tagme.auth.enabled",()->true);r.add("tagme.auth.token",()->"test-only-token");r.add("tagme.auth.channel-id",()->"c".repeat(26));}
    @Autowired JdbcTemplate db;@Autowired AuthRepository accounts;@Autowired PasswordEncoder passwords;@Autowired LocalAdminService admins;
    @Autowired ContentService content;@Autowired ContentRepository contentRows;@Autowired ReactionService reactions;@Autowired ResultsService results;
    @Autowired ParticipationRepository participation;@Autowired MissionCompletionService completion;@Autowired ParticipationResetService resets;@Autowired AdminAnalytics analytics;
    @Autowired PendingSubmissionSettlement settlement;@Autowired TestRestTemplate http;@Autowired ObjectMapper json;@Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired OperationsRepository awardRows;@Autowired PostEventRepository reportRows;@Autowired OperationsService operations;
    @MockitoSpyBean ResultsRepository resultRows;@MockitoBean MattermostClient mattermost;@MockitoBean ModerationProvider provider;
    @MockitoSpyBean AdminAuditRepository audit;
    AuthPrincipal admin,alice,bob;Instant time;
    @BeforeEach void setup() {
        db.update("UPDATE content_items SET approved_revision_id=NULL,pending_revision_id=NULL");
        for(String table:List.of("admin_audit_logs","game_participation_resets","cheer_likes","wish_comments","moderation_reviews","moderation_jobs","content_revisions","content_items","content_settings","mission_completion_evidence","submission_receipts","event_completions","mission_progress","tags","sync_results","current_sync_questionnaire","sync_questionnaires","tap_attempt_events","tap_slots","tap_attempts","profile_views","user_consent_events","user_consents","user_interests","profiles","connection_sessions","user_credentials","users","current_consent_policies","consent_policies","event_settings"))db.update("DELETE FROM "+table);
        try(var c=redis.getConnectionFactory().getConnection()){c.serverCommands().flushDb();}
        admins.run(null);admin=accounts.byAdminLogin("admin:reset-test").orElseThrow().principal();
        db.update("INSERT INTO event_settings(id,opens_at,submit_closes_at,moderation_closes_at) VALUES(1,UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 2 HOUR)");
        for(String type:List.of("PARTICIPATION","PROFILE_PUBLICATION")){db.update("INSERT INTO consent_policies VALUES(?,'reset-v1','integration policy','isolated fixture')",type);db.update("INSERT INTO current_consent_policies VALUES(?,'reset-v1')",type);}
        alice=participant("reset-alice");bob=participant("reset-bob");
        when(mattermost.byUsername("reset-alice")).thenReturn(Optional.of(new MattermostClient.Identity("reset-alice-external-fixture","reset-alice",new MattermostProfile("통합 검사","서울",1,0))));
        db.update("INSERT INTO sync_questionnaires VALUES('reset-v1',?)",java.nio.file.Files.exists(java.nio.file.Path.of("../docs/sync-questionnaire.proposal.json"))?readQuestionnaire():"{}");
        db.update("INSERT INTO current_sync_questionnaire VALUES(1,'reset-v1')");
        time=db.queryForObject("SELECT UTC_TIMESTAMP(6)",java.sql.Timestamp.class).toInstant();doAnswer(i->time).when(resultRows).now();
        clearInvocations(provider,mattermost);
    }
    String readQuestionnaire(){try{return java.nio.file.Files.readString(java.nio.file.Path.of("../docs/sync-questionnaire.proposal.json"));}catch(Exception e){throw new IllegalStateException(e);}}
    AuthPrincipal participant(String name) {
        long id=accounts.insertUser(name+"-external-fixture",name);accounts.insertCredential(id,passwords.encode("isolated participant password"));
        db.update("UPDATE users SET real_name='통합 검사',region='서울',class_no=1,mattermost_profile_synced_at=UTC_TIMESTAMP(6) WHERE id=?",id);
        db.update("INSERT INTO profiles(user_id,nickname,bio,is_public,published_at) VALUES(?,?, '통합 검사 소개',TRUE,UTC_TIMESTAMP(6))",id,name);
        db.update("INSERT INTO user_interests SELECT ?,MIN(id) FROM interests",id);
        db.update("INSERT INTO user_consents(user_id,policy_type,policy_version,agreed_at) SELECT ?,policy_type,policy_version,UTC_TIMESTAMP(6) FROM current_consent_policies",id);
        db.update("INSERT INTO mission_progress(user_id,mission_id,unlocked_at) SELECT ?,id,UTC_TIMESTAMP(6)-INTERVAL 2 MINUTE FROM missions",id);
        completion.completeShowMe(id);return accounts.byId(id).orElseThrow().principal();
    }
    ContentController.Submit text(String value,Long version){return new ContentController.Submit(value,"PLAIN","ROSE",true,version);}
    ResultsController.SyncRequest answers() {
        var questions=results.questions(alice);
        return new ResultsController.SyncRequest(questions.version(),questions.questions().stream().map(q->new ResultsController.Answer(q.id(),q.options().getFirst().id())).toList());
    }
    ParticipationResetService.ResetRequest resetBody(long generation){return new ParticipationResetService.ResetRequest(generation,"통합 검사 재참여","RESET");}
    @Test void defaultSubmissionsAndEditsArePublishedAtomicallyWithoutAnyModerationJob() {
        var wish=content.submit(alice,WISH,"direct-wish-key-001",text("즉시 공개 https://example.test",null),false);
        var cheer=content.submit(alice,CHEER,"direct-cheer-key-001",text("바로 보이는 응원",null),false);
        assertThat(wish.pendingRevision()).isNull();assertThat(wish.approvedRevision().status()).isEqualTo("APPROVED");assertThat(wish.missionCompleted()).isTrue();
        assertThat(content.feed(WISH,null).items()).extracting(ContentModels.PublicContent::content).containsExactly("즉시 공개 https://example.test");
        var edit=content.submit(alice,WISH,"direct-wish-edit-001",text("수정도 바로 공개",wish.version()),true);
        assertThat(edit.approvedRevision().content()).isEqualTo("수정도 바로 공개");assertThat(edit.pendingRevision()).isNull();
        assertThat(contentRows.revision(wish.approvedRevision().id()).content()).isEqualTo("즉시 공개 https://example.test");
        var comment=reactions.submit(bob,wish.id(),"direct-comment-001",new ReactionController.CommentRequest("바로 보이는 댓글",false));
        assertThat(comment.status()).isEqualTo("APPROVED");assertThat(reactions.comments(wish.id(),null).items()).hasSize(1);
        assertThat(reactions.like(bob,cheer.id(),true).count()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM moderation_jobs",Integer.class)).isZero();verifyNoInteractions(provider);
    }
    @Test void resetArchivesAllFourGamesAndReactionsButKeepsIdentityProfileShowMeTagsAndOtherParticipants() throws Exception {
        var wish=content.submit(alice,WISH,"reset-wish-key-001",text("보관할 소원",null),false);
        var cheer=content.submit(bob,CHEER,"other-cheer-key-001",text("다른 참가자 응원",null),false);
        reactions.submit(alice,wish.id(),"reset-comment-key-001",new ReactionController.CommentRequest("보관할 댓글",false));
        reactions.like(alice,cheer.id(),true);results.submit(alice,answers());
        var attempt=results.start(alice,"reset-tap-key-001");time=attempt.endsAt().plusMillis(100);
        results.finish(alice,new ResultsController.FinishRequest(attempt.attemptId(),List.of(1,2,3),List.of()));
        content.submit(alice,CHEER,"reset-cheer-key-001",text("보관할 응원",null),false);
        var before=analytics.detail(admin,alice.userId());assertThat(before.member().completedCount()).isEqualTo(5);long order=before.member().completionOrder();
        String profile=db.queryForObject("SELECT nickname FROM profiles WHERE user_id=?",String.class,alice.userId());
        String syncTime=db.queryForObject("SELECT submitted_at FROM sync_results WHERE user_id=?",java.sql.Timestamp.class,alice.userId()).toInstant().toString();
        var reset=resets.reset(admin,alice.userId(),"reset-all-key-0001",resetBody(0));
        assertThat(reset.generation()).isEqualTo(1);assertThat(resets.reset(admin,alice.userId(),"reset-all-key-0001",resetBody(0))).isEqualTo(reset);
        assertThat(analytics.detail(admin,alice.userId()).member().completedCount()).isEqualTo(1);
        assertThat(analytics.detail(admin,alice.userId()).member().completionOrder()).isNull();
        assertThat(participation.mission(alice.userId(),SHOW_ME).state()).isEqualTo("COMPLETED");
        for(var code:List.of(SYNC_ME,FAST_TAP,MAKE_A_WISH,SAY_SOMETHING))assertThat(participation.mission(alice.userId(),code).state()).isEqualTo("UNLOCKED");
        assertThat(db.queryForObject("SELECT nickname FROM profiles WHERE user_id=?",String.class,alice.userId())).isEqualTo(profile);
        assertThat(content.own(alice.userId(),WISH).id()).isNull();assertThat(results.sync(alice.userId())).isNull();assertThat(results.tap(alice.userId()).officialAvailable()).isTrue();
        assertThat(content.feed(WISH,null).items()).isEmpty();assertThat(content.own(bob.userId(),CHEER).id()).isEqualTo(cheer.id());assertThat(reactions.state(alice,cheer.id()).count()).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_attempt_events WHERE attempt_id=?",Integer.class,attempt.attemptId())).isEqualTo(3);
        assertThat(db.queryForObject("SELECT archived_at IS NOT NULL FROM content_items WHERE id=?",Boolean.class,wish.id())).isTrue();
        var archive=json.readTree(db.queryForObject("SELECT snapshot_json FROM game_participation_resets WHERE id=?",String.class,reset.id()));
        assertThat(archive.path("sync").size()).isEqualTo(1);assertThat(archive.path("completion").get(0).path("completion_order").asLong()).isEqualTo(order);
        assertThat(archive.path("sync").get(0).path("submitted_at").asText()).isEqualTo(syncTime);
        assertThatThrownBy(()->operations.visibility(admin,wish.id(),wish.version(),"VISIBLE","보관 기록 복구 금지")).isInstanceOf(AuthException.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE action='GAME_PARTICIPATION_RESET'",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->results.finish(alice,new ResultsController.FinishRequest(attempt.attemptId(),List.of(1,2,3),List.of()),0)).isInstanceOfSatisfying(AuthException.class,e->assertThat(e.code()).isEqualTo("PARTICIPATION_RESET"));
        assertThatThrownBy(()->content.submit(alice,WISH,"delayed-write-key-001",text("이전 화면 요청",null),false,0)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->results.finish(alice,new ResultsController.FinishRequest(attempt.attemptId(),List.of(1,2,3),List.of()),1)).isInstanceOf(AuthException.class);
        assertThat(content.submit(alice,WISH,"new-wish-after-reset",text("새 소원",null),false,1).missionCompleted()).isTrue();
        assertThat(results.submit(alice,answers(),1)).isNotNull();
        var next=results.start(alice,"new-tap-after-reset",1);assertThat(next.generation()).isGreaterThan(attempt.generation());time=next.endsAt().plusMillis(100);
        results.finish(alice,new ResultsController.FinishRequest(next.attemptId(),List.of(1,2,3),List.of()),1);
        content.submit(alice,CHEER,"new-cheer-after-reset",text("새 응원",null),false,1);
        assertThat(analytics.detail(admin,alice.userId()).member().completionOrder()).isGreaterThan(order);
        assertThat(analytics.ranking(admin,"FAST_TAP",0,25).items()).hasSize(1);
    }
    @Test void concurrentResetAndRepeatedRequestsNeverRunTwoResets() throws Exception {
        content.submit(alice,WISH,"race-wish-key-001",text("동시 초기화 검사",null),false);
        try(var pool=Executors.newFixedThreadPool(4)) {
            var futures=new ArrayList<Future<ParticipationResetService.ResetRecord>>();
            for(int i=0;i<8;i++)futures.add(pool.submit(()->resets.reset(admin,alice.userId(),"concurrent-reset-001",resetBody(0))));
            long first=futures.getFirst().get(30,TimeUnit.SECONDS).id();for(var future:futures)assertThat(future.get(30,TimeUnit.SECONDS).id()).isEqualTo(first);
        }
        assertThat(participation.generation(alice.userId())).isEqualTo(1);
        assertThatThrownBy(()->resets.reset(admin,alice.userId(),"stale-reset-key-001",resetBody(0))).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->resets.reset(alice,bob.userId(),"unauthorized-key-001",resetBody(0))).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->resets.reset(admin,admin.userId(),"admin-target-key-001",resetBody(0))).isInstanceOf(AuthException.class);
    }
    @Test void resetFailureRollsBackArchivingGenerationScoresAndAudit() {
        var wish=content.submit(alice,WISH,"rollback-wish-key-001",text("롤백 검사",null),false);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("integration audit failure")).when(audit).record(anyLong(),anyString(),anyString(),anyLong(),anyString(),any(),any());
        try{assertThatThrownBy(()->resets.reset(admin,alice.userId(),"rollback-reset-key-001",resetBody(0))).isInstanceOf(RuntimeException.class);}
        finally{doCallRealMethod().when(audit).record(anyLong(),anyString(),anyString(),anyLong(),anyString(),any(),any());}
        assertThat(participation.generation(alice.userId())).isZero();assertThat(content.own(alice.userId(),WISH).id()).isEqualTo(wish.id());assertThat(participation.mission(alice.userId(),MAKE_A_WISH).state()).isEqualTo("COMPLETED");
    }
    @Test void legacyReviewTapIsAutomaticallySettledFromOriginalEventsAndNeverDuplicated() {
        var attempt=results.start(alice,"legacy-review-key-001");time=attempt.endsAt().plusMillis(100);
        results.finish(alice,new ResultsController.FinishRequest(attempt.attemptId(),List.of(2,4,6),List.of()));
        db.update("DELETE FROM mission_completion_evidence WHERE user_id=? AND mission_id=(SELECT id FROM missions WHERE code='FAST_TAP')",alice.userId());
        db.update("DELETE FROM submission_receipts WHERE user_id=? AND source_kind='GAME_ATTEMPT'",alice.userId());
        db.update("UPDATE mission_progress SET status='UNLOCKED',completed_at=NULL WHERE user_id=? AND mission_id=(SELECT id FROM missions WHERE code='FAST_TAP')",alice.userId());
        db.update("UPDATE tap_attempts SET status='REVIEW_REQUIRED',review_reason='UNUSUAL_RATE' WHERE id=?",attempt.attemptId());
        settlement.processBatch();settlement.processBatch();
        assertThat(results.tap(alice.userId()).attempt().status()).isEqualTo("VALID");assertThat(participation.mission(alice.userId(),FAST_TAP).state()).isEqualTo("COMPLETED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_attempt_events",Integer.class)).isEqualTo(3);verifyNoInteractions(provider);
    }
    @Test void legacyPendingContentBecomesPublicWithoutInvokingAProvider() {
        var wish=content.submit(alice,WISH,"legacy-wish-key-001",text("기존 대기 소원",null),false);
        db.update("UPDATE content_revisions SET moderation_status='REVIEW_REQUIRED',reason_code='PROVIDER_NOT_CONFIGURED' WHERE id=?",wish.approvedRevision().id());
        db.update("UPDATE content_items SET pending_revision_id=approved_revision_id,approved_revision_id=NULL WHERE id=?",wish.id());
        db.update("INSERT INTO moderation_jobs(revision_id,status) VALUES (?,'REVIEW_REQUIRED')",wish.approvedRevision().id());
        settlement.processBatch();settlement.processBatch();
        assertThat(content.own(alice.userId(),WISH).approvedRevision().content()).isEqualTo("기존 대기 소원");
        assertThat(content.own(alice.userId(),WISH).pendingRevision()).isNull();
        assertThat(db.queryForObject("SELECT status FROM moderation_jobs WHERE revision_id=?",String.class,wish.approvedRevision().id())).isEqualTo("APPROVED");
        verifyNoInteractions(provider);
    }
    @Test void settlingLegacyHiddenContentClosesTheQueueWithoutRestoringPublication() {
        var wish=content.submit(alice,WISH,"legacy-hidden-wish-001",text("숨김을 유지할 소원",null),false);
        db.update("UPDATE content_revisions SET moderation_status='REVIEW_REQUIRED',reason_code='CONTENT_HIDDEN' WHERE id=?",wish.approvedRevision().id());
        db.update("UPDATE content_items SET visibility_status='HIDDEN',pending_revision_id=approved_revision_id,approved_revision_id=NULL WHERE id=?",wish.id());
        db.update("INSERT INTO moderation_jobs(revision_id,status) VALUES (?,'REVIEW_REQUIRED')",wish.approvedRevision().id());
        settlement.processBatch();
        assertThat(content.own(alice.userId(),WISH).visibility()).isEqualTo("HIDDEN");assertThat(content.own(alice.userId(),WISH).pendingRevision()).isNull();
        assertThat(content.feed(WISH,null).items()).isEmpty();assertThat(contentRows.reviewQueue()).isEmpty();verifyNoInteractions(provider);
    }
    @Test void resetRacingAnOldEditNeverResurrectsAnActiveSlot() throws Exception {
        var wish=content.submit(alice,WISH,"race-old-wish-001",text("이전 소원",null),false);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var reset=pool.submit(()->{start.await();return resets.reset(admin,alice.userId(),"reset-edit-race-001",resetBody(0));});
            var edit=pool.submit(()->{start.await();try{content.submit(alice,WISH,"delayed-edit-race-001",text("이전 화면의 수정",wish.version()),true,0);}catch(AuthException stale){assertThat(stale.code()).isEqualTo("PARTICIPATION_RESET");}return true;});
            start.countDown();reset.get(30,TimeUnit.SECONDS);edit.get(30,TimeUnit.SECONDS);
        }
        assertThat(content.own(alice.userId(),WISH).id()).isNull();assertThat(participation.generation(alice.userId())).isEqualTo(1);
    }
    @Test void resetNeverUnlocksAnUntaggedMission() {
        db.update("DELETE FROM mission_progress WHERE user_id=? AND mission_id=(SELECT id FROM missions WHERE code='SAY_SOMETHING')",alice.userId());
        content.submit(alice,WISH,"untagged-wish-001",text("태그 경계 검사",null),false);
        resets.reset(admin,alice.userId(),"untagged-reset-001",resetBody(0));
        assertThat(participation.mission(alice.userId(),SAY_SOMETHING).state()).isEqualTo("LOCKED");
        assertThatThrownBy(()->content.submit(alice,CHEER,"untagged-cheer-001",text("해금 없는 응원",null),false,1)).isInstanceOfSatisfying(AuthException.class,e->assertThat(e.code()).isEqualTo("MISSION_LOCKED"));
    }
    @Test void resetGenerationHeaderProtectsTheActualParticipantHttpWrite() {
        Browser participant=new Browser();var login=participant.call(HttpMethod.POST,"/api/v1/auth/login",Map.of("username","reset-alice","password","isolated participant password"),participant.csrf());
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        resets.reset(admin,alice.userId(),"http-generation-reset",resetBody(0));
        String csrf=participant.csrf();
        assertThat(participant.call(HttpMethod.POST,"/api/v1/tap/start",null,csrf).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        participant.generation=1;
        assertThat(participant.call(HttpMethod.POST,"/api/v1/tap/start",null,csrf).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void directPublicationsAndThreeHundredTapsRemainEligibleForRankingsAndResetRemovesTheirCurrentMetrics() {
        var wish=content.submit(alice,WISH,"proof-wish-key-001",text("순위 확인 소원",null),false);
        var cheer=content.submit(alice,CHEER,"proof-cheer-key-001",text("순위 확인 응원",null),false);
        reactions.submit(bob,wish.id(),"proof-comment-key-001",new ReactionController.CommentRequest("공개 댓글",false));reactions.like(bob,cheer.id(),true);
        time=time.minusSeconds(20);var attempt=results.start(alice,"proof-rapid-tap-001");time=attempt.endsAt().plusMillis(100);
        results.finish(alice,new ResultsController.FinishRequest(attempt.attemptId(),java.util.stream.IntStream.range(0,300).boxed().toList(),List.of()));
        var at=db.queryForObject("SELECT UTC_TIMESTAMP(6)",java.sql.Timestamp.class).toInstant();var facts=awardRows.awardFacts(at);
        assertThat(facts.proofs()).filteredOn(p->p.userId()==alice.userId()).hasSize(4).allMatch(AwardModels.Proof::valid);
        assertThat(facts.taps()).anyMatch(t->t.userId()==alice.userId()&&t.count()==300);
        var before=reportRows.metrics(at).stream().collect(java.util.stream.Collectors.toMap(PostEventModels.Metric::key,PostEventModels.Metric::value));
        assertThat(before).containsEntry("publicWISH",1L).containsEntry("publicCHEER",1L).containsEntry("publicCOMMENT",1L).containsEntry("validHearts",1L);
        resets.reset(admin,alice.userId(),"proof-reset-key-001",resetBody(0));
        var after=reportRows.metrics(db.queryForObject("SELECT UTC_TIMESTAMP(6)",java.sql.Timestamp.class).toInstant()).stream().collect(java.util.stream.Collectors.toMap(PostEventModels.Metric::key,PostEventModels.Metric::value));
        assertThat(after).containsEntry("wishAuthors",0L).containsEntry("cheerAuthors",0L).containsEntry("tapParticipants",0L).containsEntry("publicCOMMENT",0L).containsEntry("validHearts",0L);
    }
    @Test void resetHttpRequiresAdminCsrfAndExplicitConfirmationAndReturnsNoArchiveBodies() {
        content.submit(alice,WISH,"http-reset-wish-001",text("개인 보관 본문",null),false);
        Browser browser=new Browser();String path="/api/v1/admin/members/"+alice.userId()+"/reset-games";
        assertThat(browser.call(HttpMethod.POST,path,resetBody(0),null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        browser.call(HttpMethod.POST,"/api/v1/auth/login",Map.of("username","admin:reset-test","password","integration reset operator password"),browser.csrf());
        assertThat(browser.call(HttpMethod.POST,path,resetBody(0),null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(browser.call(HttpMethod.POST,path,new ParticipationResetService.ResetRequest(0,"검사","WRONG"),browser.csrf()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var response=browser.call(HttpMethod.POST,path,resetBody(0),browser.csrf());assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("generation",1).doesNotContainKeys("snapshot","snapshotJson","content");
        assertThat(response.toString()).doesNotContain("개인 보관 본문");
    }
    class Browser {
        final Map<String,String> cookies=new HashMap<>();
        long generation=0;
        ResponseEntity<Map> call(HttpMethod method,String path,Object body,String csrf){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.add("Idempotency-Key","http-reset-request-001");h.add("X-Participation-Generation",Long.toString(generation));if(!cookies.isEmpty())h.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));if(csrf!=null)h.add("X-XSRF-TOKEN",csrf);var response=http.exchange(path,method,new HttpEntity<>(body,h),Map.class);for(String cookie:response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE,List.of())){String[] pair=cookie.split(";",2)[0].split("=",2);cookies.put(pair[0],pair.length==2?pair[1]:"");}return response;}
        String csrf(){return (String)call(HttpMethod.GET,"/api/v1/csrf",null,null).getBody().get("token");}
    }
}
