package com.tagme.integration;

import com.tagme.auth.*;
import com.tagme.discovery.*;
import com.tagme.participation.ParticipationRepository;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration") @Testcontainers @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="tagme.content.worker-enabled=false")
class DiscoveryIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));r.add("tagme.auth.enabled",()->true);r.add("tagme.auth.token",()->"test-only-token");r.add("tagme.auth.channel-id",()->"c".repeat(26));r.add("tagme.auth.hmac-key",()->Base64.getEncoder().encodeToString(new byte[32]));}
    @Autowired JdbcTemplate db;@MockitoSpyBean AuthRepository accounts;@Autowired PasswordEncoder passwords;@Autowired DiscoveryService service;@Autowired ParticipationRepository participation;@Autowired com.tagme.profile.ProfilePhotoService photos;@Autowired ObjectMapper json;@Autowired TestRestTemplate http;@Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @MockitoSpyBean DiscoveryRepository repository;@MockitoBean MattermostClient mattermost;
    AuthPrincipal actor,bob,charlie;String hash;long interest;final Map<String,String> cookies=new HashMap<>();static final String PASSWORD="a long test password";
    @BeforeEach void setup(){
        reset(repository,mattermost,accounts);cookies.clear();
        db.update("DELETE FROM cheer_likes");
        db.update("UPDATE content_items SET approved_revision_id=NULL,pending_revision_id=NULL");
        db.update("DELETE FROM content_revisions");db.update("DELETE FROM content_items");
        for(String t:List.of("profile_views","current_sync_questionnaire","sync_results","sync_questionnaires","user_consent_events","user_consents","user_interests","profiles","user_credentials","users","current_consent_policies","consent_policies","event_settings"))db.update("DELETE FROM "+t);
        try(var c=redis.getConnectionFactory().getConnection()){c.serverCommands().flushDb();}
        db.update("INSERT INTO event_settings(id,opens_at,submit_closes_at,moderation_closes_at) VALUES (1,UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 2 HOUR)");
        for(String type:List.of("PARTICIPATION","PROFILE_PUBLICATION")){db.update("INSERT INTO consent_policies VALUES (?,'test-v1','테스트','테스트 전용')",type);db.update("INSERT INTO current_consent_policies VALUES (?,'test-v1')",type);}
        hash=passwords.encode(PASSWORD);interest=db.queryForObject("SELECT MIN(id) FROM interests",Long.class);actor=person("alice");bob=person("bob");charlie=person("charlie");
        when(mattermost.byUsername("alice")).thenReturn(Optional.of(new MattermostClient.Identity("test-external-alice","alice",new MattermostProfile("테스트이름","서울",13,0))));
    }
    AuthPrincipal person(String name){long id=accounts.insertUser("test-external-"+name,name);accounts.insertCredential(id,hash);db.update("UPDATE users SET mattermost_profile_synced_at=UTC_TIMESTAMP(6),real_name=?,region='서울',class_no=3 WHERE id=?","비공개실명-"+name,id);db.update("INSERT INTO profiles(user_id,nickname,bio,is_public,github_url) VALUES (?,?,'자세한 소개',TRUE,'https://github.com/test-only')",id,"동료-"+name);db.update("INSERT INTO user_interests VALUES (?,?)",id,interest);db.update("INSERT INTO user_consents(user_id,policy_type,policy_version,agreed_at) SELECT ?,policy_type,policy_version,UTC_TIMESTAMP(6) FROM current_consent_policies",id);return accounts.byId(id).orElseThrow().principal();}
    DiscoveryModels.People people(){return service.people(actor,null,null,null,null,null,null);}
    @Test void listsDiscloseOnlyNicknamesInterestsAndSafeRelationships()throws Exception{
        var p=people();assertThat(p.items()).hasSize(2);assertThat(p.items()).noneMatch(x->x.id()==actor.userId());String encoded=json.writeValueAsString(p);assertThat(encoded).doesNotContain("realName","classNo","bio","githubUrl","mattermost","비공개실명","credential","answers","axisScores");assertThat(service.stats(actor).visitedCount()).isZero();
        var detail=service.open(actor,bob.userId());assertThat(detail.realName()).isEqualTo("비공개실명-bob");assertThat(detail.person().visited()).isTrue();assertThat(detail.newlyVisited()).isTrue();assertThat(detail.githubUrl()).isEqualTo("https://github.com/test-only");
    }
    @Test void concurrentOpenRequestsPreserveOneVisitAndItsFirstTimestamp()throws Exception{
        try(var pool=Executors.newFixedThreadPool(6)){List<Future<DiscoveryModels.Detail>> tasks=new ArrayList<>();for(int i=0;i<16;i++)tasks.add(pool.submit(()->service.open(actor,bob.userId())));int first=0;for(var task:tasks)if(task.get(30,TimeUnit.SECONDS).newlyVisited())first++;assertThat(first).isEqualTo(1);}
        var time=db.queryForObject("SELECT first_viewed_at FROM profile_views",java.sql.Timestamp.class);assertThat(service.open(actor,bob.userId()).newlyVisited()).isFalse();assertThat(db.queryForObject("SELECT first_viewed_at FROM profile_views",java.sql.Timestamp.class)).isEqualTo(time);assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views",Integer.class)).isEqualTo(1);
    }
    @Test void mutualStatsAndGraphsShowOnlyTheViewersDirectVisibleRelations(){service.open(actor,bob.userId());service.open(bob,actor.userId());service.open(charlie,bob.userId());var stats=service.stats(actor);assertThat(stats.visitedCount()).isEqualTo(1);assertThat(stats.visitorCount()).isEqualTo(1);assertThat(stats.mutualCount()).isEqualTo(1);var graph=service.graph(actor,30);assertThat(graph.nodes()).hasSize(1);assertThat(graph.nodes().getFirst().mutual()).isTrue();assertThat(graph.edges()).hasSize(2);assertThat(graph.edges()).allMatch(e->e.from()==actor.userId()||e.to()==actor.userId());}
    @Test void graphWeightUsesOnlyCurrentApprovedNonAnonymousOwnLikes() {
        service.open(actor,bob.userId());service.open(bob,actor.userId());
        db.update("INSERT INTO content_items(author_id,kind) VALUES (?,'CHEER')",bob.userId());
        long card=db.queryForObject("SELECT id FROM content_items WHERE author_id=? AND kind='CHEER'",Long.class,bob.userId());
        db.update("INSERT INTO content_revisions(item_id,revision_no,content_text,template_id,style,anonymous,submitted_at,moderation_status,idempotency_key,payload_hash) VALUES (?,1,'test','PLAIN','INK',FALSE,UTC_TIMESTAMP(6),'APPROVED','weight-test',?)",card,"a".repeat(64));
        long revision=db.queryForObject("SELECT id FROM content_revisions WHERE item_id=?",Long.class,card);
        db.update("UPDATE content_items SET approved_revision_id=? WHERE id=?",revision,card);
        db.update("INSERT INTO cheer_likes(card_id,user_id) VALUES (?,?)",card,actor.userId());
        assertThat(service.graph(actor,30).edges()).allMatch(e->e.weight()==6);
        // Card owner sees visits only: the liker identity is not exposed in their graph.
        assertThat(service.graph(bob,30).edges()).allMatch(e->e.weight()==4);
        db.update("DELETE FROM cheer_likes WHERE card_id=?",card);
        assertThat(service.graph(actor,30).edges()).allMatch(e->e.weight()==4);
        db.update("INSERT INTO cheer_likes(card_id,user_id) VALUES (?,?)",card,actor.userId());
        assertThat(service.graph(actor,30).edges()).allMatch(e->e.weight()==6);
        db.update("UPDATE content_revisions SET anonymous=TRUE WHERE id=?",revision);
        assertThat(service.graph(actor,30).edges()).allMatch(e->e.weight()==4);
        db.update("UPDATE content_revisions SET anonymous=FALSE WHERE id=?",revision);
        db.update("UPDATE content_items SET visibility_status='HIDDEN' WHERE id=?",card);
        assertThat(service.graph(actor,30).edges()).allMatch(e->e.weight()==4);
        db.update("UPDATE content_items SET visibility_status='VISIBLE',approved_revision_id=NULL WHERE id=?",card);
        assertThat(service.graph(actor,30).edges()).allMatch(e->e.weight()==4);
    }
    @Test void selfOpenNeverCreatesAnEdgeAndGraphTruncationDoesNotShrinkFullStats(){assertThat(service.open(actor,actor.userId()).newlyVisited()).isFalse();assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views",Integer.class)).isZero();service.open(actor,bob.userId());service.open(actor,charlie.userId());var graph=service.graph(actor,1);assertThat(graph.nodes()).hasSize(1);assertThat(graph.truncated()).isTrue();assertThat(service.stats(actor).visitedCount()).isEqualTo(2);assertThatThrownBy(()->service.graph(actor,31)).isInstanceOf(AuthException.class);}
    @Test void hidingAndPolicyChangesFilterEveryReadWithoutDeletingHistory(){service.open(actor,bob.userId());service.open(bob,actor.userId());db.update("UPDATE profiles SET is_public=FALSE WHERE user_id=?",bob.userId());assertThat(people().items()).noneMatch(p->p.id()==bob.userId());assertThat(service.recommended(actor)).noneMatch(p->p.id()==bob.userId());assertThat(service.graph(actor,30).nodes()).isEmpty();assertThat(service.stats(actor).mutualCount()).isZero();assertThatThrownBy(()->service.open(actor,bob.userId())).isInstanceOf(AuthException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views",Integer.class)).isEqualTo(2);db.update("INSERT INTO consent_policies VALUES ('PROFILE_PUBLICATION','test-v2','새 테스트','테스트 전용')");db.update("UPDATE current_consent_policies SET policy_version='test-v2' WHERE policy_type='PROFILE_PUBLICATION'");assertThatThrownBy(()->people()).isInstanceOf(AuthException.class);}
    @Test void suspendedWithdrawnOrIncompleteProfilesAreNotDiscoverable(){db.update("UPDATE users SET status='SUSPENDED' WHERE id=?",bob.userId());db.update("UPDATE profiles SET nickname='' WHERE user_id=?",charlie.userId());assertThat(people().items()).isEmpty();db.update("UPDATE users SET status='WITHDRAWN' WHERE id=?",bob.userId());assertThatThrownBy(()->service.open(actor,bob.userId())).isInstanceOf(AuthException.class);}
    @Test void cursorAndLiteralNicknameFiltersAreBoundedAndNeverSearchRealNames(){db.update("UPDATE profiles SET nickname='동료_%!' WHERE user_id=?",bob.userId());var found=service.people(actor,null,1,"_%!",interest,null,null);assertThat(found.items()).hasSize(1);assertThat(found.items().getFirst().id()).isEqualTo(bob.userId());assertThat(service.people(actor,null,20,"비공개실명",null,null,null).items()).isEmpty();var first=service.people(actor,null,1,null,null,null,null);var second=service.people(actor,first.nextCursor(),1,null,null,null,null);assertThat(first.items().getFirst().id()).isNotEqualTo(second.items().getFirst().id());assertThat(second.nextCursor()).isNull();assertThatThrownBy(()->service.people(actor,null,31,null,null,null,null)).isInstanceOf(AuthException.class);assertThatThrownBy(()->service.people(actor,null,20,null,null,"X",null)).isInstanceOf(AuthException.class);}
    @Test void recommendationsUsePublicSharedInterestsAndUnvisitedOrdering(){service.open(actor,charlie.userId());var rows=service.recommended(actor);assertThat(rows.getFirst().id()).isEqualTo(bob.userId());assertThat(rows.getFirst().sharedInterests()).hasSize(1);assertThat(rows.getFirst().sameType()).isFalse();assertThat(rows.getFirst().type()).isNull();}
    @Test void typeExplanationsRequireAStoredResultAndTheSameQuestionnaireVersion(){
        String def="""
            {"title":"테스트","axes":[{"id":"a","negative":"I","positive":"E","tie":"NEGATIVE"}],"questions":[{"id":"q","text":"테스트 질문","options":[{"id":"a","label":"A","scores":{"a":-1}},{"id":"b","label":"B","scores":{"a":1}}]}],"types":[{"code":"I","title":"테스트 I","description":"테스트"},{"code":"E","title":"테스트 E","description":"테스트"}]}
            """;
        db.update("INSERT INTO sync_questionnaires VALUES ('test-v1',?),('test-v2',?)",def,def);
        for(var p:List.of(actor,bob,charlie))db.update("INSERT INTO sync_results(user_id,questionnaire_version,answers_json,axis_scores_json,type_code,payload_hash,submitted_at) VALUES (?,?,'[]','{}','I',?,UTC_TIMESTAMP(6))",p.userId(),p==charlie?"test-v2":"test-v1","a".repeat(64));
        var rows=people().items();assertThat(rows.stream().filter(p->p.id()==bob.userId()).findFirst().orElseThrow().sameType()).isTrue();assertThat(rows.stream().filter(p->p.id()==charlie.userId()).findFirst().orElseThrow().sameType()).isFalse();assertThat(service.people(actor,null,20,null,null,"I","test-v1").items()).hasSize(1);
    }
    @Test void failedDetailTransactionRollsBackTheVisit(){doAnswer(i->{i.callRealMethod();throw new org.springframework.dao.DataAccessResourceFailureException("test rollback");}).when(repository).visit(anyLong(),anyLong());assertThatThrownBy(()->service.open(actor,bob.userId())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views",Integer.class)).isZero();}
    @Test void hideDuringOpenWaitsOnTheSameUserLockAndWins()throws Exception{
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);var calls=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(i->{if(calls.incrementAndGet()==2)attempting.countDown();return i.callRealMethod();}).when(accounts).lockUser(bob.userId());
        try(var pool=Executors.newFixedThreadPool(2)){
            var hide=pool.submit(()->new TransactionTemplate(manager).executeWithoutResult(s->{accounts.lockUser(bob.userId());db.update("UPDATE profiles SET is_public=FALSE WHERE user_id=?",bob.userId());locked.countDown();try{assertThat(release.await(15,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new RuntimeException(e);}}));
            assertThat(locked.await(15,TimeUnit.SECONDS)).isTrue();var open=pool.submit(()->{assertThatThrownBy(()->service.open(actor,bob.userId())).isInstanceOf(AuthException.class);});assertThat(attempting.await(15,TimeUnit.SECONDS)).isTrue();release.countDown();hide.get(30,TimeUnit.SECONDS);open.get(30,TimeUnit.SECONDS);
        }finally{release.countDown();}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views",Integer.class)).isZero();
    }
    @Test void concurrentReciprocalOpensLockUsersInOneOrder()throws Exception{
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->service.open(actor,bob.userId()));var b=pool.submit(()->service.open(bob,actor.userId()));a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views",Integer.class)).isEqualTo(2);assertThat(service.stats(actor).mutualCount()).isEqualTo(1);assertThat(service.stats(bob).mutualCount()).isEqualTo(1);
    }
    @Test void eventClosureAllowsReadSurfacesButNoNewDetailVisits(){db.update("UPDATE event_settings SET submit_closes_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");assertThat(people().items()).hasSize(2);assertThat(service.stats(actor).visitedCount()).isZero();assertThatThrownBy(()->service.open(actor,bob.userId())).isInstanceOf(AuthException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM profile_views",Integer.class)).isZero();}
    @Test void legacyUnsafeLinksAreNeverDisclosedInDetails(){db.update("UPDATE profiles SET github_url='javascript:alert(1)',blog_url='https://name:secret@example.com' WHERE user_id=?",bob.userId());var d=service.open(actor,bob.userId());assertThat(d.githubUrl()).isNull();assertThat(d.blogUrl()).isNull();}
    ResponseEntity<Map> raw(HttpMethod method,String path,Object body,String csrf){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(!cookies.isEmpty())h.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));if(csrf!=null)h.add("X-XSRF-TOKEN",csrf);var r=http.exchange("/api/v1"+path,method,new HttpEntity<>(body,h),Map.class);for(String c:r.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)){var p=c.split(";",2)[0].split("=",2);if(p.length==2)cookies.put(p[0],p[1]);}return r;}
    ResponseEntity<Map> send(String path,Object body){var c=raw(HttpMethod.GET,"/csrf",null,null);return raw(HttpMethod.POST,path,body,(String)c.getBody().get("token"));}
    @Test void httpRequiresSessionAndCsrfAndHasNoGetDetailBypass(){assertThat(raw(HttpMethod.GET,"/discovery/people",null,null).getStatusCode().value()).isEqualTo(401);assertThat(send("/auth/login",Map.of("username","alice","password",PASSWORD)).getStatusCode().value()).isEqualTo(200);assertThat(raw(HttpMethod.POST,"/discovery/profiles/"+bob.userId()+"/open",null,null).getStatusCode().value()).isEqualTo(403);assertThat(raw(HttpMethod.GET,"/discovery/profiles/"+bob.userId()+"/open",null,null).getStatusCode().value()).isEqualTo(405);var opened=send("/discovery/profiles/"+bob.userId()+"/open",Map.of("viewerId",charlie.userId(),"visited",true));assertThat(opened.getStatusCode().value()).isEqualTo(200);assertThat(db.queryForObject("SELECT viewer_id FROM profile_views",Long.class)).isEqualTo(actor.userId());assertThat(raw(HttpMethod.GET,"/profiles",null,null).getHeaders().getCacheControl()).contains("no-store");}
    @Test void photosRequireOpeningTheProfileAndRecheckCurrentAccountAfterExternalIo() {
        db.update("UPDATE users SET mattermost_picture_updated_at=1 WHERE id=?",bob.userId());String external=accounts.byId(bob.userId()).orElseThrow().mattermostId();
        assertThatThrownBy(()->photos.photo(actor,bob.userId(),false)).isInstanceOf(AuthException.class);verify(mattermost,never()).photo(anyString());
        service.open(actor,bob.userId());byte[] png={(byte)137,80,78,71,13,10,26,10,0};
        when(mattermost.photo(external)).thenAnswer(i->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return png;});assertThat(photos.photo(actor,bob.userId(),false)).isEqualTo(png);
        when(mattermost.photo(external)).thenAnswer(i->{db.update("UPDATE users SET status='SUSPENDED' WHERE id=?",bob.userId());return png;});assertThatThrownBy(()->photos.photo(actor,bob.userId(),false)).isInstanceOf(AuthException.class);
    }
    @Test void optionalInterestsAndBioDoNotExcludeAnImportedNicknameProfile() {
        db.update("DELETE FROM user_interests WHERE user_id=?",bob.userId());db.update("UPDATE profiles SET bio=NULL WHERE user_id=?",bob.userId());
        var person=people().items().stream().filter(p->p.id()==bob.userId()).findFirst().orElseThrow();assertThat(person.interests()).isEmpty();assertThat(service.open(actor,bob.userId()).realName()).isEqualTo("비공개실명-bob");
    }
}
