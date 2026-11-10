package com.tagme.auth;

/** Mattermost Markdown presentation only. No OTP values are logged or retained here. */
final class OtpMessage {
    private OtpMessage() { }
    static String format(String purposeLabel,String code,int ttlSeconds) {
        String validity=ttlSeconds%60==0 ? ttlSeconds/60+"분("+ttlSeconds+"초)" : ttlSeconds+"초";
        return "**[TAG ME] "+purposeLabel+" 인증 코드**\n\n"
                +"아래 인증번호를 복사해 입력해 주세요.\n\n"
                +"**"+code+"**\n\n"
                +"- 유효 시간: "+validity+"\n"
                +"- 인증번호를 다른 사람에게 공유하지 마세요.\n"
                +"- 요청한 적이 없다면 이 메시지를 무시해 주세요.";
    }
}
