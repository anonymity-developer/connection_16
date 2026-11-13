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
    @Autowired TapRankingService rankings;@Autowired com.tagme.discovery.DiscoveryService discovery;
    @MockitoSpyBean ResultsRepository resultRows;@MockitoBean MattermostClient mattermost;@MockitoBean ModerationProvider provider;
    @MockitoSpyBean AdminAuditRepository audit;
    @Autowired MemberControlService controls;@Autowired ErrorReportService reports;@Autowired ParticipationService participationService;@Autowired OtpStore otpStore;@Autowired AuthService authService;
    AuthPrincipal admin,alice,bob;Instant time;
    @BeforeEach void setup() {
        db.update("UPDATE content_items SET approved_revision_id=NULL,pending_revision_id=NULL");
        for(String table:List.of("error_reports","account_deactivations","show_me_regrant_blocks","admin_audit_logs","game_participation_resets","cheer_likes","wish_comments","moderation_reviews","moderation_jobs","content_revisions","content_items","content_settings","mission_completion_evidence","submission_receipts","event_completions","mission_progress","tags","sync_results","current_sync_questionnaire","sync_questionnaires","tap_attempt_events","tap_slots","tap_attempts","profile_views","user_consent_events","user_consents","user_interests","profiles","connection_sessions","user_credentials","users","current_consent_policies","consent_policies","event_settings"))db.update("DELETE FROM "+table);
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
        db.update("INSERT INTO user_interests SELECT ?,MIN(id) FROM interests WHERE active=TRUE GROUP BY category",id);
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
    ResultsController.TapAttempt score(AuthPrincipal actor,int count) {
        var attempt=results.start(actor,UUID.randomUUID().toString());time=attempt.endsAt().plusMillis(100);
        return results.finish(actor,new ResultsController.FinishRequest(attempt.attemptId(),java.util.stream.IntStream.range(0,count).boxed().toList(),List.of()));
    }
    @Test void fourHundredCharacterWishesAndCheersRespectEachRevisionPrivacyAndKeepCommentLimit() {
        assertThat(participation.mission(alice.userId(),FAST_TAP).hint()).isEqualTo("19층과 20층 사이 계단에서 F를 찾아 NFC 태그를 읽어주세요.");
        for(var kind:List.of(WISH,CHEER)) {
            var hidden=content.submit(alice,kind,UUID.randomUUID().toString(),text("가".repeat(400),null),false);
            var visible=content.submit(bob,kind,UUID.randomUUID().toString(),new ContentController.Submit("나".repeat(400),"PLAIN","ROSE",false,null),false);
            var feed=content.feed(kind,null).items();
            var anonymous=feed.stream().filter(i->i.id()==hidden.id()).findFirst().orElseThrow();
            assertThat(anonymous.content()).hasSize(400);assertThat(anonymous.nickname()).isNull();assertThat(anonymous.profileId()).isNull();
            var named=feed.stream().filter(i->i.id()==visible.id()).findFirst().orElseThrow();
            assertThat(named.anonymous()).isFalse();assertThat(named.nickname()).isEqualTo("reset-bob");assertThat(named.profileId()).isEqualTo(bob.userId());
            var edit=content.submit(bob,kind,UUID.randomUUID().toString(),text("익명으로 변경",visible.version()),true);
            assertThat(contentRows.revision(visible.approvedRevision().id()).anonymous()).isFalse();
            assertThat(content.feed(kind,null).items().stream().filter(i->i.id()==edit.id()).findFirst().orElseThrow().profileId()).isNull();
            assertThatThrownBy(()->content.submit(alice,kind,UUID.randomUUID().toString(),text("가".repeat(401),hidden.version()),true)).isInstanceOf(AuthException.class);
        }
        var wish=content.own(alice.userId(),WISH);
        assertThatThrownBy(()->reactions.submit(bob,wish.id(),UUID.randomUUID().toString(),new ReactionController.CommentRequest("가".repeat(201),false))).isInstanceOf(AuthException.class);
        assertThat(reactions.submit(bob,wish.id(),UUID.randomUUID().toString(),new ReactionController.CommentRequest("가".repeat(200),false)).content()).hasSize(200);
    }
    @Test void contentHttpAcceptsFourHundredAndRejectsFourHundredOneWithoutChangingTheSavedBody() {
        Browser browser=new Browser();
        browser.call(HttpMethod.POST,"/api/v1/auth/login",Map.of("username","reset-alice","password","isolated participant password"),browser.csrf());
        String csrf=browser.csrf();
        for(String kind:List.of("wishes","cheers")) {
            browser.key=UUID.randomUUID().toString();
            assertThat(browser.call(HttpMethod.POST,"/api/v1/"+kind,new ContentController.Submit("가".repeat(400),"PLAIN","ROSE",false,null),csrf).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            browser.key=UUID.randomUUID().toString();
            assertThat(browser.call(HttpMethod.PUT,"/api/v1/"+kind+"/me",new ContentController.Submit("나".repeat(401),"PLAIN","ROSE",false,1L),csrf).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(content.own(alice.userId(),"wishes".equals(kind)?WISH:CHEER).latestRevision().content()).isEqualTo("가".repeat(400));
        }
    }
    @Test void recordEntitlementSurvivesBeingOvertakenAndMessagesAreOneTimeAndIdempotent() {
        var first=score(alice,10);score(bob,20);
        assertThat(rankings.board(alice).own().rank()).isEqualTo(2);
        assertThat(rankings.board(alice).own().canWriteMessage()).isTrue();
        String key="record-message-test-key-01";
        assertThat(rankings.message(alice,first.attemptId(),"<script>plain text</script>",key,0).own().message()).isEqualTo("<script>plain text</script>");
        assertThat(rankings.message(alice,first.attemptId(),"<script>plain text</script>",key,0).own().canWriteMessage()).isFalse();
        assertThatThrownBy(()->rankings.message(alice,first.attemptId(),"수정",key,0)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->rankings.message(alice,first.attemptId(),"수정","different-record-key-01",0)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->rankings.message(bob,first.attemptId(),"다른 사람 기록","other-record-key-001",0)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->rankings.message(bob,rankings.board(bob).own().attemptId(),"가".repeat(51),"long-record-key-0001",0)).isInstanceOf(AuthException.class);
        var peer=participant("tied-peer");score(peer,20);
        assertThat(rankings.board(peer).own().recordBreaker()).isFalse();
        assertThat(rankings.board(peer).own().rank()).isEqualTo(2);
        assertThat(rankings.board(peer).top()).extracting(TapRankingService.Entry::nickname).containsExactly("reset-bob","tied-peer","reset-alice");
        assertThat(rankings.message(peer,rankings.board(peer).own().attemptId(),"동점",UUID.randomUUID().toString(),0).own().message()).isEqualTo("동점");
        db.update("UPDATE event_settings SET submit_closes_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=1");
        assertThat(rankings.board(bob).own().canWriteMessage()).isFalse();
        assertThat(rankings.message(alice,first.attemptId(),"<script>plain text</script>",key,0).own().message()).isNotNull();
    }
    @Test void topTenOnlyContainsEligibleNicknamesWhileOwnerOutsideTenKeepsRecordAndEntitlement() {
        score(alice,10);
        for(int i=0;i<11;i++)score(participant("rank-peer-"+i),20+i);
        var board=rankings.board(alice);
        assertThat(board.top()).hasSize(10);assertThat(board.top().getFirst().tapCount()).isEqualTo(30);
        assertThat(board.own().rank()).isEqualTo(12);assertThat(board.own().canWriteMessage()).isTrue();
        var payload=json.valueToTree(board.top()).toString();
        assertThat(payload).doesNotContain("userId","attemptId","realName","mattermost");
        var locked=participant("rank-locked");db.update("DELETE p FROM mission_progress p JOIN missions m ON m.id=p.mission_id WHERE p.user_id=? AND m.code='FAST_TAP'",locked.userId());
        assertThatThrownBy(()->rankings.board(locked)).isInstanceOf(AuthException.class);
        db.update("UPDATE users SET status='SUSPENDED' WHERE mattermost_username='rank-peer-10'");
        assertThat(rankings.board(alice).top().getFirst().tapCount()).isEqualTo(29);
    }
    @Test void topTenNonBreakerHasOneFiftyCharacterMessageAndPublicProfileLink() {
        score(alice,90);var second=score(bob,80);
        var board=rankings.board(bob);assertThat(board.own().recordBreaker()).isFalse();
        assertThat(board.own().canWriteMessage()).isTrue();
        assertThat(board.top().get(1).profileId()).isEqualTo(bob.userId());
        assertThatThrownBy(()->rankings.message(bob,second.attemptId(),"가".repeat(51),"too-long-top-ten-key",0)).isInstanceOf(AuthException.class);
        var saved=rankings.message(bob,second.attemptId(),"가".repeat(50),"fifty-char-top-ten-key",0);
        assertThat(saved.own().message()).hasSize(50);assertThat(saved.own().canWriteMessage()).isFalse();
        assertThat(rankings.message(bob,second.attemptId(),"가".repeat(50),"fifty-char-top-ten-key",0).own().message()).hasSize(50);
    }
    @Test void rankElevenGainsPermissionOnReadAfterResetAndKeepsItAfterBeingOvertaken() {
        var leaders=new ArrayList<AuthPrincipal>();for(int i=0;i<10;i++){var peer=participant("threshold-"+i);leaders.add(peer);score(peer,100+i);}
        var attempt=score(bob,50);assertThat(rankings.board(bob).own().rank()).isEqualTo(11);
        assertThat(rankings.board(bob).own().canWriteMessage()).isFalse();
        assertThatThrownBy(()->rankings.message(bob,attempt.attemptId(),"not yet","not-top-ten-test-key",0)).isInstanceOf(AuthException.class);
        resets.reset(admin,leaders.getFirst().userId(),"threshold-reset-key",resetBody(0));
        assertThat(rankings.board(bob).own().rank()).isEqualTo(10);assertThat(rankings.board(bob).own().canWriteMessage()).isTrue();
        score(participant("new-threshold-leader"),200);
        assertThat(rankings.board(bob).own().rank()).isEqualTo(11);assertThat(rankings.board(bob).own().canWriteMessage()).isTrue();
        assertThat(rankings.message(bob,attempt.attemptId(),"권한 유지","retained-ten-test-key",0).own().message()).isEqualTo("권한 유지");
    }
    @Test void simultaneousFinishesKeepCurrentRanksAndPreviouslyEarnedPermissions() throws Exception {
        for(int i=0;i<9;i++)score(participant("concurrent-top-"+i),100+i);
        var a=results.start(alice,UUID.randomUUID().toString());var b=results.start(bob,UUID.randomUUID().toString());time=a.endsAt().plusSeconds(1);
        var taps=java.util.stream.IntStream.range(0,50).boxed().toList();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var left=pool.submit(()->{gate.await();return results.finish(alice,new ResultsController.FinishRequest(a.attemptId(),taps,List.of()));});
            var right=pool.submit(()->{gate.await();return results.finish(bob,new ResultsController.FinishRequest(b.attemptId(),taps,List.of()));});gate.countDown();
            assertThat(left.get(15,TimeUnit.SECONDS).status()).isEqualTo("VALID");assertThat(right.get(15,TimeUnit.SECONDS).status()).isEqualTo("VALID");
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_message_permissions WHERE user_id IN (?,?)",Integer.class,alice.userId(),bob.userId())).isBetween(1,2);
        // The first finisher may briefly enter tenth before the earlier-ID tied attempt overtakes it.
        for(var actor:List.of(alice,bob))if(rankings.board(actor).own().rank()==10)assertThat(rankings.board(actor).own().canWriteMessage()).isTrue();
        assertThat(List.of(rankings.board(alice).own().rank(),rankings.board(bob).own().rank())).containsExactlyInAnyOrder(10,11);
    }
    @Test void migrationPreservesLegacyLongMessagesAndArchivedEntitlements() throws Exception {
        var attempt=score(alice,10);
        db.update("UPDATE tap_record_achievements SET message_text=?,message_key='legacy-message-key',message_at=UTC_TIMESTAMP(6) WHERE attempt_id=?","가".repeat(100),attempt.attemptId());
        db.update("DELETE FROM tap_message_permissions WHERE attempt_id=?",attempt.attemptId());
        var sql=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V20__tap_top_ten_messages.sql"));
        db.update(sql.substring(sql.indexOf("INSERT INTO tap_message_permissions")));
        assertThat(rankings.board(alice).own().message()).hasSize(100);assertThat(rankings.board(alice).own().canWriteMessage()).isFalse();
        resets.reset(admin,alice.userId(),"legacy-top-ten-reset",resetBody(0));
        assertThat(rankings.board(alice).own()).isNull();
        assertThat(db.queryForObject("SELECT message_text FROM tap_message_permissions WHERE attempt_id=?",String.class,attempt.attemptId())).hasSize(100);
    }
    @Test void equalConcurrentFinishesGrantExactlyOneRecordEntitlementAndNeverCreateItOnReplay() throws Exception {
        var a=results.start(alice,UUID.randomUUID().toString());var b=results.start(bob,UUID.randomUUID().toString());time=a.endsAt().plusSeconds(1);
        var taps=java.util.stream.IntStream.range(0,80).boxed().toList();
        var bodyA=new ResultsController.FinishRequest(a.attemptId(),taps,List.of());var bodyB=new ResultsController.FinishRequest(b.attemptId(),taps,List.of());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var left=pool.submit(()->{gate.await();return results.finish(alice,bodyA);});var right=pool.submit(()->{gate.await();return results.finish(bob,bodyB);});gate.countDown();
            assertThat(left.get(15,TimeUnit.SECONDS).status()).isEqualTo("VALID");assertThat(right.get(15,TimeUnit.SECONDS).status()).isEqualTo("VALID");
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_record_achievements",Integer.class)).isEqualTo(1);
        results.finish(alice,bodyA);results.finish(bob,bodyB);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_record_achievements",Integer.class)).isEqualTo(1);
        assertThat(rankings.board(alice).top()).hasSize(2);
    }
    @Test void resetArchivesRecordMessagesAndSerializesWithAnotherParticipantsNewRecord() throws Exception {
        var old=score(bob,20);rankings.message(bob,old.attemptId(),"이전 회차 소감","old-message-test-key",0);
        var next=results.start(alice,UUID.randomUUID().toString());time=next.endsAt().plusMillis(100);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var reset=pool.submit(()->{gate.await();return resets.reset(admin,bob.userId(),"reset-record-test-001",resetBody(0));});
            var finish=pool.submit(()->{gate.await();return results.finish(alice,new ResultsController.FinishRequest(next.attemptId(),java.util.stream.IntStream.range(0,40).boxed().toList(),List.of()));});gate.countDown();
            reset.get(15,TimeUnit.SECONDS);assertThat(finish.get(15,TimeUnit.SECONDS).status()).isEqualTo("VALID");
        }
        assertThat(rankings.board(alice).top()).hasSize(1);assertThat(rankings.board(alice).own().recordBreaker()).isTrue();assertThat(rankings.board(bob).own()).isNull();
        assertThat(db.queryForObject("SELECT message_text FROM tap_message_permissions WHERE attempt_id=?",String.class,old.attemptId())).isEqualTo("이전 회차 소감");
        assertThatThrownBy(()->rankings.message(bob,old.attemptId(),"오래된 요청","old-message-test-key",0)).isInstanceOfSatisfying(AuthException.class,e->assertThat(e.code()).isEqualTo("PARTICIPATION_RESET"));
        var retry=results.start(bob,UUID.randomUUID().toString(),1);time=retry.endsAt().plusMillis(100);
        results.finish(bob,new ResultsController.FinishRequest(retry.attemptId(),java.util.stream.IntStream.range(0,60).boxed().toList(),List.of()),1);
        assertThat(rankings.board(bob).own().canWriteMessage()).isTrue();
    }
    @Test void anonymousLikesAndCommentsBoostRecommendationsWithoutDisclosingIdentitySignals() {
        var peer=participant("recommend-peer");
        // Explicit 5-point class advantage; no dependency on a particular tie lottery outcome.
        db.update("UPDATE users SET class_no=class_no+1 WHERE id=?",peer.userId());
        assertThat(discovery.recommended(alice).getFirst().id()).isEqualTo(peer.userId());
        var cheer=content.submit(bob,CHEER,UUID.randomUUID().toString(),text("익명 응원",null),false);
        reactions.like(alice,cheer.id(),true);
        assertThat(discovery.recommended(alice).getFirst().id()).isEqualTo(bob.userId());
        assertThat(json.valueToTree(discovery.recommended(alice)).toString()).doesNotContain("interaction_score","익명 응원","comment","liked");
        reactions.like(alice,cheer.id(),false);
        assertThat(discovery.recommended(alice).getFirst().id()).isEqualTo(peer.userId());
        var wish=content.submit(bob,WISH,UUID.randomUUID().toString(),text("익명 소원",null),false);
        var comment=reactions.submit(alice,wish.id(),UUID.randomUUID().toString(),new ReactionController.CommentRequest("익명 반응",false));
        assertThat(reactions.comments(wish.id(),null).items().getFirst().profileId()).isNull();
        assertThat(discovery.recommended(alice).getFirst().id()).isEqualTo(bob.userId());
        contentRows.hide(wish.id(),"HIDDEN");
        assertThat(discovery.recommended(alice).getFirst().id()).isEqualTo(peer.userId());
    }
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
    @Test void migrationBackfillsOnlyProvableActiveStrictRecordBreakersWithoutInventingMessages() throws Exception {
        var first=score(alice,10);var tie=score(bob,10);var high=score(participant("backfill-higher"),20);
        var archived=score(participant("backfill-archived"),30);
        db.update("UPDATE tap_attempts SET archived_at=UTC_TIMESTAMP(6) WHERE id=?",archived.attemptId());
        db.update("DELETE FROM tap_record_achievements");
        try(var resource=getClass().getResourceAsStream("/db/migration/V13__add_content_choices_and_tap_records.sql")) {
            String sql=new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            db.update(sql.substring(sql.indexOf("INSERT INTO tap_record_achievements")));
        }
        assertThat(db.queryForList("SELECT attempt_id FROM tap_record_achievements ORDER BY attempt_id",Long.class)).containsExactly(first.attemptId(),high.attemptId());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_record_achievements WHERE message_text IS NOT NULL OR message_key IS NOT NULL OR message_at IS NOT NULL",Integer.class)).isZero();
        assertThat(rankings.board(alice).own().canWriteMessage()).isTrue();
        assertThat(rankings.board(bob).own().recordBreaker()).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_attempts WHERE id=?",Integer.class,tie.attemptId())).isEqualTo(1);
    }
    @Test void rankingHttpRequiresLoginUnlockCsrfOwnerGenerationAndIdempotency() {
        var attempt=score(alice,20);String boardPath="/api/v1/tap/leaderboard",messagePath="/api/v1/tap/record-message";
        var body=Map.of("attemptId",attempt.attemptId(),"content","HTTP 소감 검사");
        Browser browser=new Browser();
        assertThat(browser.call(HttpMethod.GET,boardPath,null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        browser.call(HttpMethod.POST,"/api/v1/auth/login",Map.of("username","reset-alice","password","isolated participant password"),browser.csrf());
        assertThat(browser.call(HttpMethod.GET,boardPath,null,null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(browser.call(HttpMethod.POST,messagePath,body,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String csrf=browser.csrf();browser.key=null;
        assertThat(browser.call(HttpMethod.POST,messagePath,body,csrf).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        browser.key="http-record-message-001";
        assertThat(browser.call(HttpMethod.POST,messagePath,Map.of("attemptId",attempt.attemptId(),"content","가".repeat(101)),csrf).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.call(HttpMethod.POST,messagePath,Map.of("attemptId",attempt.attemptId()+1,"content","다른 시도"),csrf).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(browser.call(HttpMethod.POST,messagePath,body,csrf).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(browser.call(HttpMethod.POST,messagePath,body,csrf).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(browser.call(HttpMethod.POST,messagePath,Map.of("attemptId",attempt.attemptId(),"content","수정 불가"),csrf).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        resets.reset(admin,alice.userId(),"http-record-reset-001",resetBody(0));
        assertThat(browser.call(HttpMethod.POST,messagePath,body,csrf).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        db.update("DELETE FROM mission_progress WHERE user_id=? AND mission_id=(SELECT id FROM missions WHERE code='FAST_TAP')",alice.userId());
        assertThat(browser.call(HttpMethod.GET,boardPath,null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    MemberControlService.ChangeRequest change(String action,long generation) {return new MemberControlService.ChangeRequest(generation,"운영 지원 통합 검사",action.equals("DEACTIVATE")?"DEACTIVATE":"APPLY",action);}
    @Test void errorReportsBindReporterAndUnicodeLimitWithIdempotencyAndProtectedReads() throws Exception {
        var browser=new Browser();browser.call(HttpMethod.POST,"/api/v1/auth/login",Map.of("username","reset-alice","password","isolated participant password"),browser.csrf());
        String csrf=browser.csrf();var input=Map.of("body","😀".repeat(200),"pagePath","/t/secret-token?password=secret");
        assertThat(browser.call(HttpMethod.POST,"/api/v1/error-reports",input,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(browser.call(HttpMethod.POST,"/api/v1/error-reports",Map.of("body","신고자 위조 검사","pagePath","/","reporterId",bob.userId()),csrf).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var first=browser.call(HttpMethod.POST,"/api/v1/error-reports",input,csrf);assertThat(first.getStatusCode()).as("report response: %s",first.getBody()).isEqualTo(HttpStatus.OK);
        var replay=browser.call(HttpMethod.POST,"/api/v1/error-reports",input,csrf);assertThat(replay.getStatusCode()).as("report retry: %s",replay.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        var row=reports.list(admin,0,25).items().getFirst();assertThat(row.reporterId()).isEqualTo(alice.userId());assertThat(row.body()).isEqualTo("😀".repeat(200));assertThat(row.pagePath()).isEqualTo("/t/[tag]");
        browser.key="report-too-long-001";assertThat(browser.call(HttpMethod.POST,"/api/v1/error-reports",Map.of("body","가".repeat(201),"pagePath","/"),csrf).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.call(HttpMethod.GET,"/api/v1/admin/error-reports",null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var guest=new Browser();assertThat(guest.call(HttpMethod.POST,"/api/v1/error-reports",input,guest.csrf()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reports.list(admin,0,1).total()).isEqualTo(1);
        for(int i=0;i<4;i++)reports.submit(alice,"report-rate-check-"+i,new ErrorReportService.ReportRequest("추가 신고 "+i,"/"));
        assertThatThrownBy(()->reports.submit(alice,"report-rate-overflow",new ErrorReportService.ReportRequest("추가 신고 제한","/"))).isInstanceOf(AuthException.class).satisfies(e->assertThat(((AuthException)e).status()).isEqualTo(429));
    }
    @Test void revokeOnlyOneMissionArchivesItsSlotAndAllowsReplayWhileOthersStayIntact() {
        var wish=content.submit(alice,WISH,"control-wish-0001",text("보관할 소원",null),false);
        var cheer=content.submit(alice,CHEER,"control-cheer-0001",text("유지할 응원",null),false);
        var record=controls.change(admin,alice.userId(),"MAKE_A_WISH","control-revoke-001",change("REVOKE",0));
        assertThat(record.generation()).isEqualTo(1);assertThat(participation.mission(alice.userId(),MAKE_A_WISH).state()).isEqualTo("UNLOCKED");
        assertThat(db.queryForObject("SELECT archived_at IS NOT NULL FROM content_items WHERE id=?",Boolean.class,wish.id())).isTrue();
        assertThat(content.own(alice.userId(),CHEER).id()).isEqualTo(cheer.id());assertThat(participation.mission(alice.userId(),SAY_SOMETHING).state()).isEqualTo("COMPLETED");
        assertThat(controls.change(admin,alice.userId(),"MAKE_A_WISH","control-revoke-001",change("REVOKE",0)).id()).isEqualTo(record.id());
        assertThatThrownBy(()->content.submit(alice,WISH,"stale-wish-000001",text("오래된 화면",null),false,0)).isInstanceOf(AuthException.class);
        assertThat(content.submit(alice,WISH,"new-round-wish-01",text("새 소원",null),false,1).id()).isNotEqualTo(wish.id());
        assertThatThrownBy(()->controls.change(admin,alice.userId(),"FAST_TAP","stale-control-001",change("LOCK",0))).isInstanceOf(AuthException.class);
    }
    @Test void lockingARealTagCanBeUnlockedAgainAndManualGrantsDoNotInventGameResults() {
        long mission=participation.mission(alice.userId(),FAST_TAP).id();
        participation.receipt(alice.userId(),"TAG_UNLOCK",mission,participation.now());
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);String hash=ParticipationService.tokenHash(token);
        db.update("INSERT INTO tags(mission_id,token_hash) VALUES(?,?)",mission,hash);
        controls.change(admin,alice.userId(),"FAST_TAP","control-lock-0001",change("LOCK",0));
        assertThat(participation.mission(alice.userId(),FAST_TAP).state()).isEqualTo("LOCKED");
        assertThat(participationService.resolve(alice,hash).newlyUnlocked()).isTrue();
        for(var code:List.of("SYNC_ME","MAKE_A_WISH","FAST_TAP","SAY_SOMETHING"))controls.change(admin,alice.userId(),code,UUID.randomUUID().toString(),change("GRANT",participation.generation(alice.userId())));
        assertThat(participation.completion(alice.userId()).complete()).isTrue();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sync_results WHERE user_id=?",Integer.class,alice.userId())).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_attempts WHERE user_id=?",Integer.class,alice.userId())).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM content_items WHERE author_id=?",Integer.class,alice.userId())).isZero();
        assertThat(awardRows.awardFacts().proofs().stream().filter(p->p.userId()==alice.userId()&&p.sourceKind().equals("ADMIN_ADJUSTMENT")).toList()).hasSize(4).allMatch(p->!p.valid());
    }
    @Test void showMeRevocationStaysRevokedUntilAnExplicitProfileSave() {
        var old=participation.profile(alice.userId());
        controls.change(admin,alice.userId(),"SHOW_ME","revoke-show-me-01",change("REVOKE",0));
        assertThat(participationService.reconcileShowMe(alice)).isFalse();assertThat(participation.mission(alice.userId(),SHOW_ME).state()).isEqualTo("UNLOCKED");
        assertThatThrownBy(()->participationService.save(alice,new ParticipationController.ProfileRequest(old.nickname(),old.bio(),old.githubUrl(),old.blogUrl(),old.interestIds(),List.of(),old.version()))).isInstanceOf(AuthException.class);
        var p=participation.profile(alice.userId());
        participationService.save(alice,new ParticipationController.ProfileRequest(p.nickname(),p.bio(),p.githubUrl(),p.blogUrl(),p.interestIds(),List.of(),p.version()));
        assertThat(participation.mission(alice.userId(),SHOW_ME).state()).isEqualTo("COMPLETED");
    }
    @Test void deactivationRevokesSessionsAndRelationshipsAndSameMattermostSignupStartsFresh() {
        var wish=content.submit(alice,WISH,"deactivate-wish-1",text("이전 계정 소원",null),false);
        var cheer=content.submit(bob,CHEER,"deactivate-cheer1",text("다른 참가자 응원",null),false);reactions.like(alice,cheer.id(),true);
        db.update("INSERT INTO profile_views VALUES(?,?,UTC_TIMESTAMP(6))",alice.userId(),bob.userId());
        var browser=new Browser();browser.call(HttpMethod.POST,"/api/v1/auth/login",Map.of("username","reset-alice","password","isolated participant password"),browser.csrf());
        var old=accounts.byId(alice.userId()).orElseThrow();long before=analytics.overview(admin).members();
        var record=controls.change(admin,alice.userId(),null,"deactivate-user1",change("DEACTIVATE",0));
        assertThat(controls.change(admin,alice.userId(),null,"deactivate-user1",change("DEACTIVATE",0)).id()).isEqualTo(record.id());
        assertThat(browser.call(HttpMethod.GET,"/api/v1/users/me",null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(accounts.byExternalId(old.mattermostId())).isEmpty();assertThat(analytics.detail(admin,alice.userId()).member().username()).isEqualTo("reset-alice");
        assertThat(analytics.overview(admin).members()).isEqualTo(before-1);assertThat(participation.missions(alice.userId())).allMatch(m->m.state().equals("LOCKED"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views WHERE viewer_id=? OR viewed_id=?",Integer.class,alice.userId(),alice.userId())).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM cheer_likes WHERE user_id=?",Integer.class,alice.userId())).isZero();
        assertThat(content.feed(WISH,null).items()).noneMatch(i->i.id()==wish.id());assertThat(content.own(bob.userId(),CHEER).id()).isEqualTo(cheer.id());
        assertThatThrownBy(()->discovery.open(bob,alice.userId())).isInstanceOf(AuthException.class);
        var identity=new MattermostClient.Identity(old.mattermostId(),old.username(),new MattermostProfile("통합 검사","서울",1,0));when(mattermost.byId(old.mattermostId())).thenReturn(Optional.of(identity));
        String challenge="S".repeat(43);otpStore.issue(challenge,new OtpStore.Challenge(OtpStore.Purpose.SIGN_UP,old.mattermostId(),0,0,""),"012345");
        var fresh=authService.signup(challenge,"012345","new fixture password","127.0.0.91");
        assertThat(fresh.id()).isNotEqualTo(alice.userId());assertThat(fresh.onboardingRequired()).isTrue();assertThat(participation.missions(fresh.id())).allMatch(m->m.state().equals("LOCKED"));
        var interest=db.queryForObject("SELECT MIN(id) FROM interests",Long.class);
        participationService.save(fresh.principal(),new ParticipationController.ProfileRequest("새 계정","다시 첫 S",null,null,db.queryForList("SELECT MIN(id) FROM interests WHERE active=TRUE GROUP BY category",Long.class),Arrays.stream(com.tagme.participation.ParticipationModels.PolicyType.values()).map(type->new ParticipationController.Acceptance(type,"reset-v1")).toList(),0));
        assertThat(participation.mission(fresh.id(),SHOW_ME).state()).isEqualTo("COMPLETED");assertThat(participation.generation(fresh.id())).isZero();
    }
    @Test void memberControlsRequireAdminCsrfAndRollbackAndSerializeRepeatedRequests() throws Exception {
        assertThatThrownBy(()->controls.change(alice,bob.userId(),"SYNC_ME","not-admin-000001",change("GRANT",0))).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->controls.change(admin,admin.userId(),null,"admin-target-001",change("DEACTIVATE",0))).isInstanceOf(AuthException.class);
        var browser=new Browser();browser.call(HttpMethod.POST,"/api/v1/auth/login",Map.of("username","admin:reset-test","password","integration reset operator password"),browser.csrf());
        String path="/api/v1/admin/members/"+alice.userId()+"/missions/SYNC_ME";
        assertThat(browser.call(HttpMethod.POST,path,change("GRANT",0),null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("isolated audit failure")).when(audit).record(anyLong(),anyString(),anyString(),anyLong(),anyString(),any(),any());
        try{assertThatThrownBy(()->controls.change(admin,alice.userId(),"SHOW_ME","rollback-change1",change("REVOKE",0))).isInstanceOf(RuntimeException.class);}
        finally{doCallRealMethod().when(audit).record(anyLong(),anyString(),anyString(),anyLong(),anyString(),any(),any());}
        assertThat(participation.generation(alice.userId())).isZero();assertThat(participation.mission(alice.userId(),SHOW_ME).state()).isEqualTo("COMPLETED");
        try(var pool=Executors.newFixedThreadPool(3)) {
            var futures=new ArrayList<Future<ParticipationResetService.ResetRecord>>();for(int i=0;i<5;i++)futures.add(pool.submit(()->controls.change(admin,alice.userId(),"SYNC_ME","concurrent-grant1",change("GRANT",0))));
            long first=futures.getFirst().get(30,TimeUnit.SECONDS).id();for(var f:futures)assertThat(f.get(30,TimeUnit.SECONDS).id()).isEqualTo(first);
        }
        assertThat(participation.generation(alice.userId())).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence WHERE user_id=? AND source_kind='ADMIN_ADJUSTMENT'",Integer.class,alice.userId())).isEqualTo(1);
    }
    class Browser {
        final Map<String,String> cookies=new HashMap<>();
        String key="http-reset-request-001";
        long generation=0;
        ResponseEntity<Map> call(HttpMethod method,String path,Object body,String csrf){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(key!=null)h.add("Idempotency-Key",key);h.add("X-Participation-Generation",Long.toString(generation));if(!cookies.isEmpty())h.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));if(csrf!=null)h.add("X-XSRF-TOKEN",csrf);var response=http.exchange(path,method,new HttpEntity<>(body,h),Map.class);for(String cookie:response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE,List.of())){String[] pair=cookie.split(";",2)[0].split("=",2);cookies.put(pair[0],pair.length==2?pair[1]:"");}return response;}
        String csrf(){return (String)call(HttpMethod.GET,"/api/v1/csrf",null,null).getBody().get("token");}
    }
}
