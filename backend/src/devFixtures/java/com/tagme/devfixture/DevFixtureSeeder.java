package com.tagme.devfixture;

import com.tagme.auth.DevFixtureMattermostClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Synthetic local data. This class is compiled only with -PdevFixtures=true. */
@Component
@Profile("dev-fixtures")
public final class DevFixtureSeeder implements ApplicationRunner {
    public static final List<String> TAG_TOKENS = List.of(
            "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE",
            "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI",
            "AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM",
            "BAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ");

    private static final List<Peer> PEERS = List.of(
            new Peer("관계친구 민서", "민서", "서로 프로필을 확인한 강한 관계예요.", List.of("IT·플랫폼", "프론트엔드"), 2, 0, "EBP"),
            new Peer("강한연결 지우", "지우", "공통 관심사와 성향이 같은 동료예요.", List.of("IT·플랫폼", "백엔드"), 3, 0, "EBP"),
            new Peer("내가본 현우", "현우", "내가 먼저 프로필을 확인한 동료예요.", List.of("자동차·모빌리티", "클라우드·DevOps"), 1, 0, "EBT"),
            new Peer("나를본 수빈", "수빈", "나를 먼저 확인한 동료예요.", List.of("의료·헬스케어", "AI·머신러닝"), 1, 0, "ELP"),
            new Peer("새동료 도윤", "도윤", "아직 방문 관계가 없는 동료예요.", List.of("커머스·유통", "기획·UI/UX"), 0, 0, "ELT"),
            new Peer("게임입문 하린", "하린", "FAST TAP을 조금 해 본 동료예요.", List.of("게임·콘텐츠", "게임 개발"), 2, 18, "XBP"),
            new Peer("게임중수 예준", "예준", "FAST TAP 기록을 더 쌓은 동료예요.", List.of("게임·콘텐츠", "시스템 SW"), 3, 57, "XBT"),
            new Peer("거의완료 서아", "서아", "미션 하나만 남긴 동료예요.", List.of("제조·스마트팩토리", "클라우드·DevOps"), 4, 83, "XLP"),
            new Peer("다모은 유진", "유진", "다섯 미션을 모두 완료한 동료예요.", List.of("금융·핀테크", "백엔드"), 5, 126, "XLT"),
            new Peer("조용한 태윤", "태윤", "아직 미션을 시작하지 않은 동료예요.", List.of("공공·교육", "데이터"), 0, 0, "EBP"),
            new Peer("서로본 지안", "지안", "양쪽에서 프로필을 열어 본 동료예요.", List.of("IT·플랫폼", "프론트엔드"), 1, 0, "EBT"),
            new Peer("서로본 준서", "준서", "관심사는 다르지만 서로 탐색한 동료예요.", List.of("반도체·전자", "AI·머신러닝"), 2, 21, "ELP"),
            new Peer("내가본 예지", "예지", "내가 먼저 궁금해한 동료예요.", List.of("커머스·유통", "모바일 앱"), 3, 44, "ELT"),
            new Peer("내가본 건우", "건우", "백엔드 관심사를 공유하는 동료예요.", List.of("금융·핀테크", "백엔드"), 4, 72, "XBP"),
            new Peer("나를본 다은", "다은", "나의 프로필을 먼저 확인한 동료예요.", List.of("로봇·IoT", "클라우드·DevOps"), 5, 109, "XBT"),
            new Peer("나를본 정우", "정우", "탐색 방향을 확인하기 위한 동료예요.", List.of("게임·콘텐츠", "게임 개발"), 0, 0, "XLP"),
            new Peer("새동료 소율", "소율", "아직 나와 프로필을 열어 보지 않았어요.", List.of("공공·교육", "프론트엔드"), 1, 0, "XLT"),
            new Peer("새동료 시우", "시우", "다른 동료들과만 탐색 관계가 있어요.", List.of("자동차·모빌리티", "데이터"), 2, 32, "EBP"),
            new Peer("미션중 수아", "수아", "콘텐츠 미션을 차근차근 진행 중이에요.", List.of("의료·헬스케어", "AI·머신러닝"), 3, 51, "XBP"),
            new Peer("완주한 연우", "연우", "모든 미션을 끝내고 새 동료를 찾고 있어요.", List.of("제조·스마트팩토리", "풀스택"), 5, 138, "XLT"));

    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final TransactionTemplate tx;
    private final DevFixtureSafety safety;

    public DevFixtureSeeder(JdbcTemplate db, PasswordEncoder passwords,
            org.springframework.transaction.PlatformTransactionManager manager, DevFixtureSafety safety) {
        this.db = db;
        this.passwords = passwords;
        this.tx = new TransactionTemplate(manager);
        this.safety = safety;
    }

    @Override
    public void run(ApplicationArguments args) {
        String testHash = passwords.encode("test1234");
        tx.executeWithoutResult(ignored -> seed(testHash));
    }

    private void seed(String testHash) {
        safety.verify();
        Instant now = Instant.now();
        seedEvent(now);
        seedPolicies(now);
        seedQuestionnaire();

        long testId = upsertUser(DevFixtureMattermostClient.EXTERNAL_ID, "test", "테스트 사용자");
        db.update("""
                INSERT INTO user_credentials(user_id,password_hash,password_updated_at,credential_version)
                VALUES (?,?,UTC_TIMESTAMP(6),1)
                ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash),password_updated_at=VALUES(password_updated_at),credential_version=credential_version+1
                """, testId, testHash);
        upsertProfile(testId, "테스트 나", "관계와 미션 상태를 직접 확인하는 로컬 테스트 계정입니다.");
        replaceInterests(testId, List.of("IT·플랫폼", "프론트엔드", "백엔드"));
        consent(testId, now);
        seedTestProgress(testId, now);

        long[] ids = new long[PEERS.size()];
        for (int index = 0; index < PEERS.size(); index++) {
            Peer peer = PEERS.get(index);
            String suffix = String.format("%02d", index + 2);
            ids[index] = upsertUser("devfixture" + "0".repeat(14) + suffix, "fixture" + suffix, peer.realName());
            upsertProfile(ids[index], peer.nickname(), peer.bio());
            replaceInterests(ids[index], peer.interests());
            consent(ids[index], now.minus(index + 1L, ChronoUnit.HOURS));
            seedPeerProgress(ids[index], peer, now.minus(index + 2L, ChronoUnit.HOURS));
            seedSync(ids[index], peer.typeCode(), now.minus(index + 1L, ChronoUnit.HOURS));
            if (peer.tapCount() > 0) seedTap(ids[index], peer.tapCount(), now.minus(index + 1L, ChronoUnit.HOURS));
        }

        // Keep repeated starts deterministic even when the isolated fixture volume is preserved.
        db.update("""
                DELETE pv FROM profile_views pv
                JOIN users viewer ON viewer.id=pv.viewer_id
                JOIN users viewed ON viewed.id=pv.viewed_id
                WHERE viewer.mattermost_id LIKE 'devfixture%' OR viewed.mattermost_id LIKE 'devfixture%'
                """);

        // Logged-in test user: 4 mutual, 5 outbound-only, 5 inbound-only, 6 unrelated peers.
        for (int index : List.of(0, 1, 10, 11)) {
            visit(testId, ids[index], now.minus(18L - index, ChronoUnit.HOURS));
            visit(ids[index], testId, now.minus(17L - index, ChronoUnit.HOURS));
        }
        for (int index : List.of(2, 4, 5, 12, 13))
            visit(testId, ids[index], now.minus(15L - index / 2L, ChronoUnit.HOURS));
        for (int index : List.of(3, 6, 7, 14, 15))
            visit(ids[index], testId, now.minus(14L - index / 2L, ChronoUnit.HOURS));

        // Peer-to-peer edges make the synthetic population varied beyond the logged-in user's direct graph.
        visit(ids[0], ids[2], now.minus(12, ChronoUnit.HOURS));
        visit(ids[2], ids[0], now.minus(11, ChronoUnit.HOURS));
        visit(ids[3], ids[4], now.minus(10, ChronoUnit.HOURS));
        visit(ids[5], ids[6], now.minus(9, ChronoUnit.HOURS));
        visit(ids[6], ids[5], now.minus(8, ChronoUnit.HOURS));
        visit(ids[7], ids[8], now.minus(7, ChronoUnit.HOURS));
        visit(ids[9], ids[10], now.minus(6, ChronoUnit.HOURS));
        visit(ids[11], ids[12], now.minus(5, ChronoUnit.HOURS));
        visit(ids[12], ids[13], now.minus(4, ChronoUnit.HOURS));
        visit(ids[14], ids[16], now.minus(3, ChronoUnit.HOURS));
        visit(ids[16], ids[17], now.minus(2, ChronoUnit.HOURS));
        visit(ids[18], ids[19], now.minus(1, ChronoUnit.HOURS));

        seedTags();
    }

    private void seedEvent(Instant now) {
        db.update("""
                INSERT INTO event_settings(id,opens_at,submit_closes_at,moderation_closes_at,game_duration,profile_preparation_enabled,settings_version)
                VALUES (1,?,?,?,?,TRUE,1)
                ON DUPLICATE KEY UPDATE opens_at=VALUES(opens_at),submit_closes_at=VALUES(submit_closes_at),
                    moderation_closes_at=VALUES(moderation_closes_at),game_duration=VALUES(game_duration),profile_preparation_enabled=TRUE,
                    settings_version=settings_version+1
                """, Timestamp.from(now.minus(1, ChronoUnit.DAYS)), Timestamp.from(now.plus(30, ChronoUnit.DAYS)),
                Timestamp.from(now.plus(31, ChronoUnit.DAYS)), 10);
    }

    private void seedPolicies(Instant now) {
        for (String type : List.of("PARTICIPATION", "PROFILE_PUBLICATION")) {
            db.update("""
                    INSERT INTO consent_policies(policy_type,policy_version,title,body) VALUES (?,'local-fixture-v1',?,?)
                    ON DUPLICATE KEY UPDATE title=VALUES(title),body=VALUES(body)
                    """, type, "로컬 fixture 안내", "로컬 Docker 테스트 전용 가상 동의입니다. 실제 운영 동의가 아닙니다.");
            db.update("""
                    INSERT INTO current_consent_policies(policy_type,policy_version) VALUES (?,'local-fixture-v1')
                    ON DUPLICATE KEY UPDATE policy_version=VALUES(policy_version)
                    """, type);
        }
    }

    private void seedQuestionnaire() {
        try {
            String json = new ClassPathResource("preview/sync-demo.json").getContentAsString(StandardCharsets.UTF_8);
            db.update("""
                    INSERT INTO sync_questionnaires(version,definition_json) VALUES ('local-fixture-v1',CAST(? AS JSON))
                    ON DUPLICATE KEY UPDATE definition_json=VALUES(definition_json)
                    """, json);
            db.update("""
                    INSERT INTO current_sync_questionnaire(id,version) VALUES (1,'local-fixture-v1')
                    ON DUPLICATE KEY UPDATE version=VALUES(version)
                    """);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot load local fixture questionnaire", failure);
        }
    }

    private long upsertUser(String externalId, String username, String realName) {
        db.update("""
                INSERT INTO users(mattermost_id,mattermost_username,real_name,region,class_no,role,status,mattermost_profile_synced_at)
                VALUES (?,?,?,'서울',16,'USER','ACTIVE',UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE mattermost_username=VALUES(mattermost_username),real_name=VALUES(real_name),region='서울',class_no=16,
                    role='USER',status='ACTIVE',mattermost_profile_synced_at=UTC_TIMESTAMP(6)
                """, externalId, username, realName);
        return db.queryForObject("SELECT id FROM users WHERE mattermost_id=?", Long.class, externalId);
    }

    private void upsertProfile(long userId, String nickname, String bio) {
        db.update("""
                INSERT INTO profiles(user_id,nickname,bio,is_public,published_at,updated_at)
                VALUES (?,?,?,TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE nickname=VALUES(nickname),bio=VALUES(bio),is_public=TRUE,
                    published_at=COALESCE(published_at,UTC_TIMESTAMP(6)),updated_at=UTC_TIMESTAMP(6),version=version+1
                """, userId, nickname, bio);
    }

    private void replaceInterests(long userId, List<String> names) {
        db.update("DELETE FROM user_interests WHERE user_id=?", userId);
        for (String name : names) {
            Long id = db.queryForObject("SELECT id FROM interests WHERE name=?", Long.class, name);
            db.update("INSERT INTO user_interests(user_id,interest_id) VALUES (?,?)", userId, id);
        }
    }

    private void consent(long userId, Instant agreedAt) {
        for (String type : List.of("PARTICIPATION", "PROFILE_PUBLICATION")) {
            db.update("""
                    INSERT INTO user_consents(user_id,policy_type,policy_version,agreed_at,withdrawn_at)
                    VALUES (?,?,'local-fixture-v1',?,NULL)
                    ON DUPLICATE KEY UPDATE agreed_at=VALUES(agreed_at),withdrawn_at=NULL
                    """, userId, type, Timestamp.from(agreedAt));
        }
    }

    private void seedTestProgress(long userId, Instant now) {
        db.update("DELETE FROM event_completions WHERE user_id=?", userId);
        db.update("""
                DELETE evidence FROM mission_completion_evidence evidence
                JOIN missions mission ON mission.id=evidence.mission_id
                WHERE evidence.user_id=? AND mission.code<>'SHOW_ME'
                """, userId);
        db.update("""
                DELETE progress FROM mission_progress progress
                JOIN missions mission ON mission.id=progress.mission_id
                WHERE progress.user_id=? AND mission.code<>'SHOW_ME'
                """, userId);
        db.update("DELETE FROM sync_results WHERE user_id=?", userId);
        db.update("DELETE FROM submission_receipts WHERE user_id=? AND source_kind IN ('TAG_UNLOCK','SYNC_RESULT')", userId);
        progress(userId, "SHOW_ME", "COMPLETED", now.minus(1, ChronoUnit.HOURS));
    }

    private void seedPeerProgress(long userId, Peer peer, Instant at) {
        List<String> order = peer.tapCount() > 0
                ? List.of("SHOW_ME", "FAST_TAP", "SYNC_ME", "MAKE_A_WISH", "SAY_SOMETHING")
                : List.of("SHOW_ME", "SYNC_ME", "MAKE_A_WISH", "FAST_TAP", "SAY_SOMETHING");
        for (int index = 0; index < peer.completedMissions(); index++)
            progress(userId, order.get(index), "COMPLETED", at.plus(index, ChronoUnit.MINUTES));
        if (peer.completedMissions() == 5) {
            db.update("""
                    INSERT INTO event_completions(user_id,completion_order,completed_at)
                    VALUES (?,(SELECT next_value FROM event_completion_sequence WHERE id=1),?)
                    ON DUPLICATE KEY UPDATE completed_at=VALUES(completed_at)
                    """, userId, Timestamp.from(at.plus(5, ChronoUnit.MINUTES)));
            db.update("UPDATE event_completion_sequence SET next_value=GREATEST(next_value,(SELECT COALESCE(MAX(completion_order),0)+1 FROM event_completions)) WHERE id=1");
        }
    }

    private void progress(long userId, String code, String status, Instant at) {
        db.update("""
                INSERT INTO mission_progress(user_id,mission_id,status,unlocked_at,completed_at)
                SELECT ?,id,?,?,IF(?='COMPLETED',?,NULL) FROM missions WHERE code=?
                ON DUPLICATE KEY UPDATE status=VALUES(status),unlocked_at=VALUES(unlocked_at),completed_at=VALUES(completed_at)
                """, userId, status, Timestamp.from(at.minus(1, ChronoUnit.MINUTES)), status, Timestamp.from(at), code);
    }

    private void seedSync(long userId, String type, Instant at) {
        db.update("""
                INSERT INTO sync_results(user_id,questionnaire_version,answers_json,axis_scores_json,type_code,payload_hash,submitted_at)
                VALUES (?,'local-fixture-v1',JSON_OBJECT(),JSON_OBJECT('conversation',1,'activity',1),?,REPEAT('0',64),?)
                ON DUPLICATE KEY UPDATE questionnaire_version=VALUES(questionnaire_version),type_code=VALUES(type_code),submitted_at=VALUES(submitted_at)
                """, userId, type, Timestamp.from(at));
    }

    private void seedTap(long userId, int tapCount, Instant at) {
        db.update("""
                INSERT INTO tap_attempts(user_id,generation,idempotency_key,starts_at,ends_at,submit_deadline,status,tap_count,submitted_at,payload_hash)
                VALUES (?,1,CONCAT('local-fixture-',?),?,?,?,'VALID',?,?,REPEAT('1',64))
                ON DUPLICATE KEY UPDATE status='VALID',tap_count=VALUES(tap_count),submitted_at=VALUES(submitted_at),review_reason=NULL
                """, userId, userId, Timestamp.from(at.minusSeconds(12)), Timestamp.from(at.minusSeconds(2)),
                Timestamp.from(at.plusSeconds(3)), tapCount, Timestamp.from(at));
        Long attempt = db.queryForObject("SELECT id FROM tap_attempts WHERE user_id=? AND generation=1", Long.class, userId);
        db.update("""
                INSERT INTO tap_slots(user_id,current_attempt_id,version) VALUES (?,?,1)
                ON DUPLICATE KEY UPDATE current_attempt_id=VALUES(current_attempt_id),version=version+1
                """, userId, attempt);
        db.update("""
                INSERT INTO submission_receipts(user_id,source_kind,source_id,received_at)
                VALUES (?,'GAME_ATTEMPT',?,?)
                ON DUPLICATE KEY UPDATE received_at=VALUES(received_at)
                """, userId, attempt, Timestamp.from(at));
        Long receipt = db.queryForObject("SELECT id FROM submission_receipts WHERE user_id=? AND source_kind='GAME_ATTEMPT' AND source_id=?",
                Long.class, userId, attempt);
        db.update("""
                INSERT INTO mission_completion_evidence(user_id,mission_id,source_kind,source_id,receipt_order,accepted_received_at)
                SELECT ?,id,'GAME_ATTEMPT',?,?,? FROM missions WHERE code='FAST_TAP'
                ON DUPLICATE KEY UPDATE source_kind=VALUES(source_kind),source_id=VALUES(source_id),
                    receipt_order=VALUES(receipt_order),accepted_received_at=VALUES(accepted_received_at)
                """, userId, attempt, receipt, Timestamp.from(at));
    }

    private void visit(long viewer, long viewed, Instant at) {
        db.update("""
                INSERT INTO profile_views(viewer_id,viewed_id,first_viewed_at) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE first_viewed_at=LEAST(first_viewed_at,VALUES(first_viewed_at))
                """, viewer, viewed, Timestamp.from(at));
    }

    private void seedTags() {
        List<String> codes = List.of("SYNC_ME", "MAKE_A_WISH", "FAST_TAP", "SAY_SOMETHING");
        db.update("DELETE FROM tags");
        for (int index = 0; index < codes.size(); index++) {
            db.update("""
                    INSERT INTO tags(mission_id,token_hash,active)
                    SELECT id,?,TRUE FROM missions WHERE code=?
                    ON DUPLICATE KEY UPDATE mission_id=VALUES(mission_id),active=TRUE
                    """, sha256(TAG_TOKENS.get(index)), codes.get(index));
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Peer(String nickname, String realName, String bio, List<String> interests,
                        int completedMissions, int tapCount, String typeCode) { }
}
