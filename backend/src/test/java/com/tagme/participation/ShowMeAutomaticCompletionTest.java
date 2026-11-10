package com.tagme.participation;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.tagme.participation.ParticipationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pure mocks. MySQL concurrency is separately covered in ParticipationIntegrationTest. */
class ShowMeAutomaticCompletionTest {
    final ParticipationRepository repository = mock(ParticipationRepository.class);
    final CompletionRule rule = mock(CompletionRule.class);
    final MissionCompletionService completion = new MissionCompletionService(repository,List.of(rule));
    final Instant before = Instant.parse("2026-01-01T00:00:00Z");
    Mission mission(String state, boolean active) { return new Mission(1, MissionCode.SHOW_ME,"S","SHOW ME","",active,state,before,null); }
    void open() { when(repository.event(true)).thenReturn(new Event("OPEN",before,before.plusSeconds(1),true)); }
    @Test void beforeOpenClosedAndUnconfiguredNeverUnlockOrGrant() {
        for (String status : List.of("BEFORE_OPEN","CLOSED","NOT_CONFIGURED")) {
            when(repository.event(true)).thenReturn(new Event(status,before,before.plusSeconds(1),true));
            assertThat(completion.completeShowMe(1)).isFalse();
        }
        verify(repository,never()).unlock(anyLong(),anyLong(),any());
        verify(repository,never()).complete(anyLong(),any(),any(),any());
    }
    @Test void incompleteInactiveAndAlreadyCompletedProfilesNeverCreateProgress() {
        open();
        when(repository.mission(1,MissionCode.SHOW_ME)).thenReturn(mission("LOCKED",true),mission("LOCKED",false),mission("COMPLETED",true));
        when(repository.showMeEligible(1)).thenReturn(false);
        for(int i=0;i<3;i++)assertThat(completion.completeShowMe(1)).isFalse();
        verify(repository,never()).unlock(anyLong(),anyLong(),any());
        verify(repository,never()).complete(anyLong(),any(),any(),any());
    }
    @Test void preRegisteredProfileUsesProfileEvidenceAndCurrentProcessingTimeWithoutATag() {
        open();
        when(repository.mission(1,MissionCode.SHOW_ME)).thenReturn(mission("LOCKED",true),mission("UNLOCKED",true));
        when(repository.showMeEligible(1)).thenReturn(true);
        when(repository.profile(1)).thenReturn(new OwnProfile("fixture","fixture","서울",1,"소개",null,null,true,3,List.of(1L),true));
        var receipt = new Receipt(7,1,"PROFILE",3,before);
        when(repository.profileReceipt(1,3)).thenReturn(receipt);when(repository.receipt(7)).thenReturn(receipt);
        when(rule.code()).thenReturn(MissionCode.SHOW_ME);when(rule.accepts(anyLong(),any(),any())).thenReturn(true);
        when(rule.conditionReceivedAt(1,receipt)).thenReturn(before);
        Instant processing=Instant.now();
        assertThat(completion.completeShowMe(1)).isTrue();
        verify(repository).unlock(eq(1L),eq(1L),argThat(time -> !time.isBefore(processing)));
        verify(repository).complete(eq(1L),any(),eq(receipt),argThat(time -> !time.isBefore(processing)));
        verify(repository,never()).tag(anyString());
        verify(repository,never()).receipt(anyLong(),eq("TAG_UNLOCK"),anyLong(),any());
    }
}
