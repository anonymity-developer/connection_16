package com.tagme.participation;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.tagme.participation.ParticipationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShowMeCompletionRuleTest {
    private final ParticipationRepository repository = mock(ParticipationRepository.class);
    private final ShowMeCompletionRule rule = new ShowMeCompletionRule(repository);
    private final Mission mission = new Mission(7L, MissionCode.SHOW_ME, "S", "SHOW ME", "fixture", true, "UNLOCKED", Instant.EPOCH, null);
    private OwnProfile profile(String bio, List<Long> interests) {
        return new OwnProfile("fixture", "fixture", "서울", 1, bio, null, null, true, 3L, interests, true);
    }
    @Test void publicProfileWithoutBioAndInterestsCannotPassTheFinalCompletionRule() {
        when(repository.profile(1L)).thenReturn(profile(null, List.of()));
        when(repository.eligible(1L)).thenReturn(true);
        when(repository.showMeEligible(1L)).thenReturn(false);
        assertThat(rule.accepts(1L, mission, new Receipt(1L, 1L, "PROFILE", 3L, Instant.EPOCH))).isFalse();
        assertThat(rule.accepts(1L, mission, new Receipt(2L, 1L, "TAG_UNLOCK", 7L, Instant.EPOCH))).isFalse();
    }
    @Test void fullRequirementsRequireMatchingProfileVersionAndNeverTagEvidence() {
        when(repository.profile(1L)).thenReturn(profile("hello", List.of(1L)));
        when(repository.showMeEligible(1L)).thenReturn(true);
        assertThat(rule.accepts(1L, mission, new Receipt(1L, 1L, "PROFILE", 3L, Instant.EPOCH))).isTrue();
        assertThat(rule.accepts(1L, mission, new Receipt(2L, 1L, "TAG_UNLOCK", 7L, Instant.EPOCH))).isFalse();
        assertThat(rule.accepts(1L, mission, new Receipt(3L, 1L, "PROFILE", 2L, Instant.EPOCH))).isFalse();
        assertThat(rule.accepts(1L, mission, new Receipt(4L, 1L, "TAG_UNLOCK", 99L, Instant.EPOCH))).isFalse();
        assertThat(rule.accepts(1L, mission, new Receipt(5L, 1L, "SYNC_RESULT", 3L, Instant.EPOCH))).isFalse();
    }
}
