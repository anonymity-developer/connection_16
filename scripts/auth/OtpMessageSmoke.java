package com.tagme.auth;

/** Offline check against the actual formatter. Never prints codes or message bodies. */
public final class OtpMessageSmoke {
    public static void main(String[] args) {
        String sample="0"+"13579";
        for(String purpose:new String[]{"회원가입","비밀번호 변경","비밀번호 재설정"}) {
            String message=OtpMessage.format(purpose,sample,180);
            boolean valid=message.startsWith("**[TAG ME] "+purpose+" 인증 코드**\n\n")
                    &&message.contains("\n\n**"+sample+"**\n\n")
                    &&message.lines().filter(line->line.contains(sample)).count()==1
                    &&message.contains("- 유효 시간: 3분(180초)")
                    &&message.contains("- 인증번호를 다른 사람에게 공유하지 마세요.")
                    &&message.endsWith("- 요청한 적이 없다면 이 메시지를 무시해 주세요.");
            if(!valid)throw new AssertionError("OTP message format check failed; values redacted");
        }
        String another="9"+"75310";
        String configured=OtpMessage.format("회원가입",another,181);
        if(!configured.contains("**"+another+"**")||configured.contains(sample)||!configured.contains("유효 시간: 181초"))throw new AssertionError("Dynamic value check failed; values redacted");
        System.out.println("PASS: three purpose titles, standalone emphasis, leading-zero preservation, notices and dynamic TTL; no codes printed");
    }
}
