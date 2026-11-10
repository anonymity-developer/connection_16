package com.tagme.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OtpMessageTest {
    // Assert booleans only: failure reports must not include message bodies or OTP fixtures.
    @Test void titleTracksAllExistingPurposesAndCodeIsAloneWithoutPadding() {
        for(var purpose:OtpStore.Purpose.values()) {
            String sample="0"+"13579";
            String message=OtpMessage.format(purpose.label(),sample,180);
            assertThat(message.startsWith("**[TAG ME] "+purpose.label()+" 인증 코드**\n\n")).as("purpose title").isTrue();
            assertThat(message.contains("\n\n**"+sample+"**\n\n")).as("standalone emphasized code").isTrue();
            assertThat(message.lines().filter(line->line.contains(sample)).count()==1).as("code occurs on one line").isTrue();
            assertThat(message.contains("- 유효 시간: 3분(180초)")).as("server default TTL display").isTrue();
            assertThat(message.endsWith("- 요청한 적이 없다면 이 메시지를 무시해 주세요.")).as("ignore notice").isTrue();
            assertThat(message.contains("- 인증번호를 다른 사람에게 공유하지 마세요.")).as("sharing notice").isTrue();
        }
    }
    @Test void usesTheSuppliedCodeAndTtlWithoutChangingAuthenticationPolicy() {
        String first="0"+"13579",second="9"+"75310";
        String message=OtpMessage.format("회원가입",second,181);
        assertThat(message.contains("**"+second+"**")&&!message.contains(first)).as("dynamic code").isTrue();
        assertThat(message.contains("유효 시간: 181초")&&!message.contains("3분")).as("configured TTL is not hardcoded").isTrue();
    }
}
