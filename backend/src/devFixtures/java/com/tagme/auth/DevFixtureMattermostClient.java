package com.tagme.auth;

import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local fixture identity adapter. This source tree is absent from normal/production boot jars. */
@Component
@Primary
@Profile("dev-fixtures")
public final class DevFixtureMattermostClient extends MattermostClient {
    public static final String EXTERNAL_ID = "devfixture0000000000000001";

    public DevFixtureMattermostClient(AuthProperties properties) {
        super(properties);
    }

    @Override
    public Optional<Identity> byUsername(String username) {
        return "test".equals(username) ? Optional.of(identity()) : Optional.empty();
    }

    @Override
    public Optional<Identity> byId(String id) {
        return EXTERNAL_ID.equals(id) ? Optional.of(identity()) : Optional.empty();
    }

    @Override
    public void sendCode(Identity target, OtpStore.Purpose purpose, String code) {
        throw new AuthException(503, "FIXTURE_LOGIN_ONLY", "로컬 fixture에서는 test/test 로그인만 사용해 주세요.");
    }

    @Override
    public byte[] photo(String externalId) {
        return null;
    }

    private Identity identity() {
        return new Identity(EXTERNAL_ID, "test", new MattermostProfile("테스트 사용자", "서울", 16, 0));
    }
}
