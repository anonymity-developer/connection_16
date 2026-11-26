package com.tagme.operations;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.tagme.operations.AwardModels.*;
import static org.assertj.core.api.Assertions.assertThat;

class PrizeReviewCalculatorTest {
    @Test void profileRegistrationBreaksActivityTieAndOnlyValidLettersEarnTickets() {
        Instant early = Instant.parse("2026-11-12T03:00:00Z");
        Instant later = early.plusSeconds(1);
        var oldProfile = new PrizePerson(2, "테스트 가", 1);
        var newProfile = new PrizePerson(1, "테스트 나", 1);
        var facts = new Facts(new Window(later, null, null, null),
                List.of(new User(1, "나", later), new User(2, "가", early)),
                List.of(new Proof(1, "SHOW_ME", "PROFILE", 1, 1, later, true),
                        new Proof(1, "FAST_TAP", "GAME_ATTEMPT", 2, 2, later, false)),
                List.of(), List.of(), List.of(new Activity(1, 25)), List.of(), 0);

        var result = PrizeReviewCalculator.calculate(facts, List.of(newProfile, oldProfile),
                List.of(new Activity(1, 7), new Activity(2, 7)));

        assertThat(result.groups().stream().filter(group -> group.code().equals("DISCOVERY")).findFirst().orElseThrow().candidates())
                .extracting(PrizeCandidate::person).containsExactly(oldProfile, newProfile);
        assertThat(result.groups().stream().filter(group -> group.code().equals("FAST_TAP")).findFirst().orElseThrow().candidates())
                .isEmpty();
        assertThat(result.drawEntries()).containsExactly(new DrawEntry(newProfile, 1));
    }
}
