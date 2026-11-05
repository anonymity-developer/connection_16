package com.tagme.auth;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MattermostProfileTest {
    @Test void parsesNamesRegionsClassesAndLeadingZeroWithoutHardcodingSeoul() {
        assertThat(MattermostProfile.parse("홍길동[서울_09반]","","",123)).isEqualTo(new MattermostProfile("홍길동","서울",9,123));
        assertThat(MattermostProfile.parse(" 테스트 이름 [ 대전 _ 12 ] ",null,null,0)).isEqualTo(new MattermostProfile("테스트 이름","대전",12,0));
        assertThat(MattermostProfile.parse("","","테스트[부울경_3반]",1).region()).isEqualTo("부울경");
    }
    @Test void rejectsMalformedOrUnboundedClassRatherThanInventingAnIdentity() {
        for(String invalid:new String[]{"홍길동","홍길동[서울_0반]","홍길동[서울_12345반]","[서울_1]","홍길동[서울_1]suffix","홍길동[서울-1]","홍\n길동[서울_1]"})assertThat(MattermostProfile.parse(invalid,"","",0)).isNull();
    }
    @Test void preferringNamesNeverReplacesTagMeNicknameWithMattermostNickname() {
        var found=MattermostProfile.parse("홍길동[서울_1반]","","다른이름[광주_4반]",-1);assertThat(found.realName()).isEqualTo("홍길동");assertThat(found.pictureUpdatedAt()).isZero();assertThat(found.toString()).doesNotContain("홍길동");
    }
}
