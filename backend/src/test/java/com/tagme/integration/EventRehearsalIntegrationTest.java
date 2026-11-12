package com.tagme.integration;

import com.tagme.auth.*;
import com.tagme.content.*;
import com.tagme.content.ContentModels.*;
import com.tagme.participation.*;
import com.tagme.participation.ParticipationModels.MissionCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** HTTP+real MySQL/Redis rehearsal. Only MM and the external moderation provider are mocked; no fixture-completion rules. */
@Tag("integration") @Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "tagme.content.worker-enabled=false","tagme.moderation.enabled=true","spring.data.redis.timeout=1s","spring.data.redis.connect-timeout=1s",
    "spring.datasource.hikari.connection-timeout=2000","spring.datasource.hikari.validation-timeout=1000",
    "spring.datasource.hikari.data-source-properties.socketTimeout=1500","spring.datasource.hikari.data-source-properties.connectTimeout=1500"})
class EventRehearsalIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withUrlParam("connectionTimeZone","UTC").withUrlParam("forceConnectionTimeZoneToSession","true");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);
        p.add("spring.flyway.url",MYSQL::getJdbcUrl);p.add("spring.flyway.user",MYSQL::getUsername);p.add("spring.flyway.password",MYSQL::getPassword);
        p.add("spring.data.redis.host",REDIS::getHost);p.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));
        p.add("tagme.auth.enabled",()->true);p.add("tagme.auth.token",()->"rehearsal-mock-only");p.add("tagme.auth.channel-id",()->"c".repeat(26));p.add("tagme.auth.hmac-key",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @Autowired JdbcTemplate db;@Autowired TestRestTemplate http;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired org.springframework.core.env.Environment environment;
    @MockitoSpyBean ParticipationRepository participation;
    @MockitoBean MattermostClient mattermost;@MockitoBean ModerationProvider provider;
    static final String PASSWORD="rehearsal test pass";
    final Map<String,String> codes=new ConcurrentHashMap<>();final Map<MissionCode,String> tokens=new EnumMap<>(MissionCode.class);
    static final String QUESTIONS="""
      {"title":"REHEARSAL ONLY","axes":[{"id":"social","negative":"I","positive":"E","tie":"NEGATIVE"}],"questions":[{"id":"q1","text":"REHEARSAL ONLY","options":[{"id":"a","label":"A","scores":{"social":-1}},{"id":"b","label":"B","scores":{"social":1}}]}],"types":[{"code":"I","title":"TEST I","description":"TEST"},{"code":"E","title":"TEST E","description":"TEST"}]}
      """;
    @BeforeEach void isolatedFixture(){
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(MYSQL.getJdbcUrl());
        assertThat(environment.getProperty("spring.data.redis.port",Integer.class)).isEqualTo(REDIS.getMappedPort(6379));
        reset(participation,mattermost,provider);codes.clear();tokens.clear();
        db.update("UPDATE content_items SET approved_revision_id=NULL,pending_revision_id=NULL");
        for(String table:List.of("admin_audit_logs","profile_views","cheer_likes","wish_comments","moderation_reviews","moderation_jobs","content_revisions","content_items","content_settings","mission_completion_evidence","submission_receipts","event_completions","mission_progress","tap_attempt_events","tap_slots","tap_attempts","sync_results","current_sync_questionnaire","sync_questionnaires","tags","user_consent_events","user_consents","user_interests","profiles","user_credentials","users","current_consent_policies","consent_policies","event_settings"))db.update("DELETE FROM "+table);
        try(var connection=redis.getConnectionFactory().getConnection()){connection.serverCommands().flushDb();}
        db.update("UPDATE event_completion_sequence SET next_value=1 WHERE id=1");db.update("UPDATE missions SET active=TRUE");
        db.update("INSERT INTO event_settings(id,opens_at,submit_closes_at,moderation_closes_at) VALUES (1,UTC_TIMESTAMP(6)-INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6)+INTERVAL 2 HOUR)");
        for(String type:List.of("PARTICIPATION","PROFILE_PUBLICATION")){db.update("INSERT INTO consent_policies VALUES (?,'rehearsal-v1','TEST ONLY','ISOLATED TEST ONLY')",type);db.update("INSERT INTO current_consent_policies VALUES (?,'rehearsal-v1')",type);}
        int i=1;for(var code:List.of(MissionCode.SYNC_ME,MissionCode.MAKE_A_WISH,MissionCode.FAST_TAP,MissionCode.SAY_SOMETHING)){
            byte[] bytes=new byte[32];bytes[0]=(byte)i++;String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);tokens.put(code,token);
            db.update("INSERT INTO tags(mission_id,token_hash) SELECT id,? FROM missions WHERE code=?",ParticipationService.tokenHash(token),code.name());
        }
        when(mattermost.byUsername(anyString())).thenAnswer(inv->{String name=inv.getArgument(0);String id=switch(name){case "alpha"->"a";case "beta"->"b";case "control"->"c";case "early"->"e";default->null;};return id==null?Optional.empty():Optional.of(new MattermostClient.Identity(id.repeat(26),name,new MattermostProfile("리허설","서울",1,0)));});
        when(mattermost.byId(anyString())).thenAnswer(inv->{String id=inv.getArgument(0);String name=switch(id.substring(0,1)){case "a"->"alpha";case "b"->"beta";case "c"->"control";case "e"->"early";default->null;};return name==null?Optional.empty():mattermost.byUsername(name);});
        doAnswer(inv->{MattermostClient.Identity identity=inv.getArgument(0);codes.put(identity.id(),inv.getArgument(2));return null;}).when(mattermost).sendCode(any(),any(),anyString());
        when(provider.inspect(anyString())).thenReturn(new ModerationProvider.Decision("REVIEW_REQUIRED","TEST_MANUAL","TEST","rehearsal-v1"));
    }
    class Client {
        final Map<String,String> cookies=new ConcurrentHashMap<>();String csrf;long userId;
        ResponseEntity<JsonNode> raw(HttpMethod method,String path,Object body,String token,String key){
            var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
            if(!cookies.isEmpty())headers.add(HttpHeaders.COOKIE,String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));
            if(token!=null)headers.add("X-XSRF-TOKEN",token);if(key!=null)headers.add("Idempotency-Key",key);
            var response=http.exchange("/api/v1"+path,method,new HttpEntity<>(body,headers),JsonNode.class);
            for(String c:response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)){var pair=c.split(";",2)[0].split("=",2);if(pair.length==2){if(pair[1].isEmpty())cookies.remove(pair[0]);else cookies.put(pair[0],pair[1]);}}
            return response;
        }
        synchronized String csrf(){if(csrf==null)csrf=ok(raw(HttpMethod.GET,"/csrf",null,null,null),200).path("token").asText();return csrf;}
        ResponseEntity<JsonNode> send(HttpMethod method,String path,Object body){return send(method,path,body,null);}
        ResponseEntity<JsonNode> send(HttpMethod method,String path,Object body,String key){var result=raw(method,path,body,csrf(),key);if(path.startsWith("/auth/"))csrf=null;return result;}
        JsonNode get(String path){return ok(raw(HttpMethod.GET,path,null,null,null),200);}
        void login(String name){ok(send(HttpMethod.POST,"/auth/login",Map.of("username",name,"password",PASSWORD)),200);}
    }
    JsonNode ok(ResponseEntity<JsonNode> response,int status){assertThat(response.getStatusCode().value()).isEqualTo(status);return response.getBody();}
    Client signup(String name){return signup(name,null);}
    Client signup(String name,MissionCode pending){
        var client=new Client();
        if(pending!=null){ok(client.send(HttpMethod.POST,"/tags/pending",Map.of("token",tokens.get(pending))),202);assertThat(client.get("/tags/pending").path("present").asBoolean()).isTrue();}
        String challenge=ok(client.send(HttpMethod.POST,"/auth/otp/request",Map.of("purpose","SIGN_UP","username",name)),200).path("challengeId").asText();
        var identity=mattermost.byUsername(name).orElseThrow();String code=codes.get(identity.id());assertThat(code!=null&&code.matches("[0-9]{6}")).isTrue();
        var user=ok(client.send(HttpMethod.POST,"/auth/signup",Map.of("challengeId",challenge,"code",code,"password",PASSWORD)),201);client.userId=user.path("id").asLong();return client;
    }
    Map<String,Object> profile(long version){return Map.of("nickname","리허설 동료","bio","리허설 소개","interestIds",List.of(db.queryForObject("SELECT MIN(id) FROM interests",Long.class)),"expectedVersion",version,"acceptPolicies",List.of(Map.of("type","PARTICIPATION","version","rehearsal-v1"),Map.of("type","PROFILE_PUBLICATION","version","rehearsal-v1")));}
    void save(Client c){ok(c.send(HttpMethod.PUT,"/users/me/profile",profile(0)),200);assertThat(completed(c)).isEqualTo(1);}
    int completed(Client c){return c.get("/missions/collection").path("completedCount").asInt();}
    void unlock(Client c,MissionCode code){ok(c.send(HttpMethod.POST,"/tags/resolve",Map.of("token",tokens.get(code))),200);}
    JsonNode content(Client c,String kind,String key,Long version){var value=new HashMap<String,Object>();value.put("content","리허설 전용 문장");value.put("templateId","PLAIN");value.put("style","ROSE");value.put("anonymous",!kind.equals("cheers"));if(version!=null)value.put("expectedVersion",version);return ok(c.send(version==null?HttpMethod.POST:HttpMethod.PUT,"/"+kind+(version==null?"":"/me"),value,key),202);}
    long job(long revision){return db.queryForObject("SELECT id FROM moderation_jobs WHERE revision_id=?",Long.class,revision);}
    void approve(Client admin,long revision){long job=job(revision);long fence=db.queryForObject("SELECT fence FROM moderation_jobs WHERE id=?",Long.class,job);ok(admin.send(HttpMethod.POST,"/admin/moderation/"+job+"/decision",Map.of("expectedFence",fence,"decision","APPROVED","reason","ISOLATED REHEARSAL")),204);}
    Map<String,Object> answers(){return Map.of("questionnaireVersion","rehearsal-v1","answers",List.of(Map.of("questionId","q1","optionId","a")));}
    void questionnaire(){db.update("INSERT INTO sync_questionnaires VALUES ('rehearsal-v1',?)",QUESTIONS);db.update("INSERT INTO current_sync_questionnaire VALUES (1,'rehearsal-v1')");}
    @Test void realHttpJourneyAndFinalCommitRetryRemainConsistent()throws Exception{
        Client a=signup("alpha",MissionCode.SYNC_ME),b=signup("beta"),admin=signup("control");
        db.update("UPDATE users SET role='ADMIN' WHERE id=?",admin.userId);admin.cookies.clear();admin.csrf=null;admin.login("control");
        assertThat(a.get("/policies").path("ready").asBoolean()).isTrue();
        var missingConsent=new HashMap<>(profile(0));missingConsent.put("acceptPolicies",List.of());
        assertThat(a.send(HttpMethod.PUT,"/users/me/profile",missingConsent).getStatusCode().value()).isEqualTo(409);assertThat(completed(a)).isZero();
        save(a);assertThat(a.get("/discovery/recommendations").size()).isZero();save(b);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM submission_receipts WHERE user_id=? AND source_kind='TAG_UNLOCK'",Integer.class,a.userId)).isZero();
        // Ignore the prior successful save response, as after a dropped connection: stale retry cannot pay twice.
        assertThat(a.send(HttpMethod.PUT,"/users/me/profile",profile(0)).getStatusCode().value()).isEqualTo(409);
        a.login("alpha");assertThat(completed(a)).isEqualTo(1);
        assertThat(a.send(HttpMethod.POST,"/sync/submit",answers()).getStatusCode().value()).isEqualTo(403);
        byte[] wrong=new byte[32];wrong[0]=99;String unknown=Base64.getUrlEncoder().withoutPadding().encodeToString(wrong);
        assertThat(a.send(HttpMethod.POST,"/tags/resolve",Map.of("token",unknown)).getStatusCode().value()).isEqualTo(404);
        assertThat(a.send(HttpMethod.POST,"/tags/resolve",Map.of("token","invalid")).getStatusCode().value()).isEqualTo(400);
        assertThat(a.get("/tags/pending").path("present").asBoolean()).isTrue();
        var anotherTab=new Client();anotherTab.login("alpha");assertThat(completed(anotherTab)).isEqualTo(1);
        ok(a.send(HttpMethod.POST,"/tags/resume",Map.of()),200);unlock(a,MissionCode.SYNC_ME);
        for(var mission:a.get("/missions/collection").path("missions")){
            var code=mission.path("code").asText();
            if(List.of("MAKE_A_WISH","FAST_TAP","SAY_SOMETHING").contains(code))assertThat(mission.path("state").asText()).isEqualTo("LOCKED");
            if(code.equals("SYNC_ME"))assertThat(mission.path("state").asText()).isEqualTo("UNLOCKED");
        }
        assertThat(a.get("/sync/questions").path("available").asBoolean()).isFalse();questionnaire();
        assertThat(a.send(HttpMethod.POST,"/sync/submit",Map.of("questionnaireVersion","rehearsal-v1","answers",List.of())).getStatusCode().value()).isEqualTo(400);
        var sync=ok(a.send(HttpMethod.POST,"/sync/submit",answers()),200);assertThat(sync.path("type").path("code").asText()).isEqualTo("I");ok(a.send(HttpMethod.POST,"/sync/submit",answers()),200);
        unlock(b,MissionCode.SYNC_ME);ok(b.send(HttpMethod.POST,"/sync/submit",answers()),200);
        assertThat(a.get("/discovery/recommendations").isArray()).isTrue();
        var graph=a.get("/discovery/graph");assertThat(graph.path("edges").size()).isZero();
        // Unlocking SYNC never authorizes another mission's slot.
        assertThat(a.send(HttpMethod.POST,"/wishes",Map.of("content","테스트","templateId","PLAIN","style","ROSE","anonymous",true),"rehearsal-locked-wish").getStatusCode().value()).isEqualTo(403);
        for(Client c:List.of(a,b)){unlock(c,MissionCode.MAKE_A_WISH);unlock(c,MissionCode.FAST_TAP);unlock(c,MissionCode.SAY_SOMETHING);}
        JsonNode aw,bw;try(var pool=Executors.newFixedThreadPool(3)){
            a.csrf();b.csrf();var one=pool.submit(()->content(a,"wishes","rehearsal-wish-alpha",null));var repeat=pool.submit(()->content(a,"wishes","rehearsal-wish-alpha",null));var other=pool.submit(()->content(b,"wishes","rehearsal-wish-beta",null));
            aw=one.get(20,TimeUnit.SECONDS);assertThat(repeat.get(20,TimeUnit.SECONDS).path("id")).isEqualTo(aw.path("id"));bw=other.get(20,TimeUnit.SECONDS);
        }
        assertThat(aw.path("missionCompleted").asBoolean()).isTrue();assertThat(aw.path("luckScore").isNull()).isTrue();
        approve(admin,aw.path("latestRevision").path("id").asLong());approve(admin,bw.path("latestRevision").path("id").asLong());
        long original=aw.path("latestRevision").path("id").asLong();var own=a.get("/wishes/me");var edit=content(a,"wishes","rehearsal-wish-edit",own.path("version").asLong());
        assertThat(edit.path("approvedRevision").path("id").asLong()).isEqualTo(original);assertThat(edit.path("missionCompleted").asBoolean()).isTrue();approve(admin,edit.path("latestRevision").path("id").asLong());
        var tapA=ok(a.send(HttpMethod.POST,"/tap/start",Map.of(),"rehearsal-tap-alpha"),200);var tapB=ok(b.send(HttpMethod.POST,"/tap/start",Map.of(),"rehearsal-tap-beta"),200);
        assertThat(Duration.between(Instant.parse(tapA.path("serverNow").asText()),Instant.parse(tapA.path("startsAt").asText())).toSeconds()).isBetween(4L,5L);
        assertThat(Duration.between(Instant.parse(tapA.path("startsAt").asText()),Instant.parse(tapA.path("endsAt").asText())).toSeconds()).isEqualTo(10);
        long wait=Duration.between(Instant.now(),Instant.parse(tapB.path("endsAt").asText()).plusMillis(150)).toMillis();assertThat(wait).isLessThan(20000);if(wait>0)Thread.sleep(wait);
        for(var pair:List.of(Map.entry(a,tapA),Map.entry(b,tapB))){var finish=Map.of("attemptId",pair.getValue().path("attemptId").asLong(),"taps",List.of(100,200,300),"interruptions",List.of());ok(pair.getKey().send(HttpMethod.POST,"/tap/finish",finish),200);ok(pair.getKey().send(HttpMethod.POST,"/tap/finish",finish),200);}
        assertThat(a.send(HttpMethod.POST,"/tap/start",Map.of(),"rehearsal-tap-reuse").getStatusCode().value()).isEqualTo(409);
        var ac=content(a,"cheers","rehearsal-cheer-alpha",null);approve(admin,ac.path("latestRevision").path("id").asLong());
        assertThat(completed(a)).isEqualTo(5);assertThat(completed(b)).isEqualTo(4);
        var failure=new AtomicBoolean(true);doAnswer(inv->{inv.callRealMethod();if(((Long)inv.getArgument(0))==b.userId&&failure.getAndSet(false))throw new DataAccessResourceFailureException("isolated final commit failure");return null;}).when(participation).complete(eq(b.userId),any(),any(),any());
        var cheer=Map.of("content","리허설 전용 문장","templateId","PLAIN","style","ROSE","anonymous",false);
        assertThat(b.send(HttpMethod.POST,"/cheers",cheer,"rehearsal-cheer-beta").getStatusCode().is5xxServerError()).isTrue();
        assertThat(completed(b)).isEqualTo(4);assertThat(db.queryForObject("SELECT COUNT(*) FROM event_completions WHERE user_id=?",Integer.class,b.userId)).isZero();
        var bc=content(b,"cheers","rehearsal-cheer-beta",null);
        assertThat(completed(b)).isEqualTo(5);
        var editBeforePublication=content(b,"cheers","rehearsal-cheer-beta-edit",bc.path("version").asLong());
        approve(admin,editBeforePublication.path("latestRevision").path("id").asLong());
        long card=ac.path("id").asLong();ok(b.send(HttpMethod.PUT,"/cheers/"+card+"/like",Map.of()),200);ok(b.send(HttpMethod.PUT,"/cheers/"+card+"/like",Map.of()),200);ok(b.send(HttpMethod.DELETE,"/cheers/"+card+"/like",null),204);
        assertThat(b.get("/cheers/"+card+"/like").path("liked").asBoolean()).isFalse();ok(b.send(HttpMethod.PUT,"/cheers/"+card+"/like",Map.of()),200);
        long wish=bw.path("id").asLong();var comment=Map.of("content","테스트 응원 댓글","revealProfile",false);var cr=ok(a.send(HttpMethod.POST,"/wishes/"+wish+"/comments",comment,"rehearsal-comment-one"),202);
        // Resolve the test comment job by persisted linkage; never read or print real content.
        long commentRevision=db.queryForObject("SELECT r.id FROM content_revisions r JOIN wish_comments c ON c.content_item_id=r.item_id WHERE c.author_id=? AND c.wish_id=?",Long.class,a.userId,wish);approve(admin,commentRevision);
        assertThat(a.get("/discovery/graph").path("edges")).isEqualTo(graph.path("edges"));
        assertThat(a.raw(HttpMethod.GET,"/admin/awards",null,null,null).getStatusCode().value()).isEqualTo(403);
        for(String category:List.of("FIRST_COMPLETION","COMMENT_KING","POPULAR_CHEER","FAST_TAP")){var report=admin.get("/admin/awards?category="+category);assertThat(report.path("provisional").asBoolean()).isTrue();assertThat(report.path("total").asInt()).isEqualTo(2);assertThat(report.path("invalidProofs").asInt()).isZero();}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Integer.class)).isEqualTo(10);assertThat(db.queryForObject("SELECT COUNT(*) FROM event_completions",Integer.class)).isEqualTo(2);
        a.login("alpha");assertThat(completed(a)).isEqualTo(5);assertThat(completed(anotherTab)).isEqualTo(5);
        var post=admin.get("/admin/results");assertThat(post.path("data").path("finalizationAvailable").asBoolean()).isFalse();
        assertThat(post.path("data").path("candidates").size()).isEqualTo(8);
        assertThat(a.raw(HttpMethod.GET,"/admin/results",null,null,null).getStatusCode().value()).isEqualTo(403);
        assertThat(post.path("data").path("metrics").findValuesAsText("key")).contains("earnedFive","validHearts","mutualRelations");
        for(var expected:Map.of("registered",2,"earnedFive",2,"validHearts",1,"publicCOMMENT",1,"mutualRelations",0).entrySet()){
            var value=java.util.stream.StreamSupport.stream(post.path("data").path("metrics").spliterator(),false).filter(m->m.path("key").asText().equals(expected.getKey())).findFirst().orElseThrow();assertThat(value.path("value").asInt()).isEqualTo(expected.getValue());
        }


    }
    @Test void preRegistrationSurvivesLoginAndOnlyReconcilesAfterOpen(){
        db.update("UPDATE event_settings SET opens_at=UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,submit_closes_at=UTC_TIMESTAMP(6)+INTERVAL 2 HOUR,moderation_closes_at=UTC_TIMESTAMP(6)+INTERVAL 3 HOUR,profile_preparation_enabled=TRUE");
        var c=signup("early");ok(c.send(HttpMethod.PUT,"/users/me/profile",profile(0)),200);assertThat(completed(c)).isZero();
        c.login("early");assertThat(c.get("/missions/collection").path("completedCount").asInt()).isZero();
        db.update("UPDATE event_settings SET opens_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");
        assertThat(ok(c.send(HttpMethod.POST,"/missions/show-me/reconcile",Map.of()),200).path("newlyCompleted").asBoolean()).isTrue();
        assertThat(ok(c.send(HttpMethod.POST,"/missions/show-me/reconcile",Map.of()),200).path("newlyCompleted").asBoolean()).isFalse();assertThat(completed(c)).isEqualTo(1);
    }
    @Test void deadlineAndMissingOtherTagCannotBeBypassed(){
        var c=signup("alpha");save(c);unlock(c,MissionCode.SYNC_ME);questionnaire();
        assertThat(c.send(HttpMethod.POST,"/tap/start",Map.of(),"rehearsal-no-tap-tag").getStatusCode().value()).isEqualTo(403);
        unlock(c,MissionCode.FAST_TAP);db.update("UPDATE event_settings SET submit_closes_at=UTC_TIMESTAMP(6)+INTERVAL 10 SECOND");
        assertThat(c.send(HttpMethod.POST,"/tap/start",Map.of(),"rehearsal-too-near-end").getStatusCode().isError()).isTrue();
        db.update("UPDATE event_settings SET submit_closes_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");
        assertThat(c.send(HttpMethod.POST,"/sync/submit",answers()).getStatusCode().value()).isEqualTo(403);assertThat(completed(c)).isEqualTo(1);
    }
    void awaitDependency(Callable<Boolean> probe)throws Exception {
        var deadline=Instant.now().plusSeconds(15);
        while(Instant.now().isBefore(deadline)){try{if(probe.call())return;}catch(Exception ignored){/* isolated dependency may still reconnect */}Thread.sleep(200);}
        throw new AssertionError("Isolated dependency did not recover before deadline");
    }
    @Test @EnabledIfEnvironmentVariable(named="TAGME_REHEARSAL_FAULTS",matches="1")
    void pausedEphemeralDependenciesFailClosedAndRecoverWithoutDeletingData()throws Exception{
        var c=signup("alpha");save(c);long before=db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Long.class);
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try{assertThat(c.raw(HttpMethod.GET,"/users/me",null,null,null).getStatusCode().isError()).isTrue();}finally{REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();}
        awaitDependency(()->{try(var connection=redis.getConnectionFactory().getConnection()){return "PONG".equals(connection.ping());}});
        c.csrf=null;c.login("alpha");assertThat(completed(c)).isEqualTo(1);
        MYSQL.getDockerClient().pauseContainerCmd(MYSQL.getContainerId()).exec();
        try{assertThat(c.raw(HttpMethod.GET,"/missions/collection",null,null,null).getStatusCode().isError()).isTrue();}finally{MYSQL.getDockerClient().unpauseContainerCmd(MYSQL.getContainerId()).exec();}
        awaitDependency(()->db.queryForObject("SELECT 1",Integer.class)==1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_completion_evidence",Long.class)).isEqualTo(before);
        c.csrf=null;c.login("alpha");assertThat(completed(c)).isEqualTo(1);
    }
}
