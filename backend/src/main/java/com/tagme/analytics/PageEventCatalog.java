package com.tagme.analytics;

import java.util.*;

public final class PageEventCatalog {
    private PageEventCatalog() {}
    public record Screen(String id,String name,String mission) {}
    public static final List<Screen> SCREENS=List.of(
        new Screen("HOME","홈·SSAFY 수집판",null),new Screen("MISSION_OVERVIEW","미션 안내",null),
        new Screen("SHOW_ME","SHOW ME","SHOW_ME"),new Screen("PROFILE","프로필 설정","SHOW_ME"),
        new Screen("SYNC_ME","SYNC ME","SYNC_ME"),new Screen("WISH","소원 호수·작성","MAKE_A_WISH"),
        new Screen("WISH_LIST","소원 목록","MAKE_A_WISH"),new Screen("WISH_DETAIL","소원과 댓글","MAKE_A_WISH"),
        new Screen("FAST_TAP","FAST TAP","FAST_TAP"),new Screen("CHEER","YOU GOT THIS","SAY_SOMETHING"),
        new Screen("DISCOVERY_RECOMMENDED","추천 동료",null),new Screen("DISCOVERY_PEOPLE","전체 동료",null),
        new Screen("DISCOVERY_GRAPH","프로필로 이어진 동료",null),new Screen("DISCOVERY_SELECTION","선택한 연결 동료",null),new Screen("PROFILE_DETAIL","동료 프로필",null),
        new Screen("ACCOUNT","마이페이지",null),new Screen("LOGIN","로그인",null),new Screen("SIGNUP","회원가입",null),
        new Screen("PASSWORD_RESET","비밀번호 재설정",null),new Screen("PASSWORD_CHANGE","비밀번호 변경",null),
        new Screen("SIGNUP_CODE","가입 인증",null),new Screen("RESET_CODE","재설정 인증",null),new Screen("CHANGE_CODE","변경 인증",null),
        new Screen("NFC_TAG","NFC 미션 발견",null),new Screen("COLLECTION_COMPLETE","다섯 글자 완성",null),new Screen("COLLECTION_REWARD","전체 수집 축하",null),
        new Screen("NOT_FOUND","찾을 수 없는 화면",null),new Screen("ERROR_REPORT","오류 신고",null),new Screen("MISSION_REWARD","글자 획득 안내",null));
    public static final Map<String,Set<String>> STEPS=Map.of(
        "PROFILE",Set.of("FORM"),"SHOW_ME",Set.of("RESULT"),"SYNC_ME",Set.of("RESULT"),
        "WISH",Set.of("COMPOSE","EDIT","RESULT"),"WISH_LIST",Set.of("COMPOSE","EDIT","RESULT"),
        "CHEER",Set.of("COMPOSE","EDIT","RESULT"),
        "FAST_TAP",Set.of("READY","COUNTDOWN","PLAYING","RESULT","PRACTICE_COUNTDOWN","PRACTICE_PLAYING","PRACTICE_RESULT"));
    public static Screen screen(String id) { return SCREENS.stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(()->invalid()); }
    public static String missionScreen(String code) { return switch(code) {case "SHOW_ME"->"PROFILE";case "SYNC_ME"->"SYNC_ME";case "MAKE_A_WISH"->"WISH";case "FAST_TAP"->"FAST_TAP";case "SAY_SOMETHING"->"CHEER";default->throw invalid();}; }
    public static com.tagme.auth.AuthException invalid() { return new com.tagme.auth.AuthException(400,"INVALID_ANALYTICS_EVENT","이벤트 형식을 확인해 주세요."); }
}
