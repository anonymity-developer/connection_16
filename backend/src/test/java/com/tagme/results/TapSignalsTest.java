package com.tagme.results;

import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TapSignalsTest {
    @Test void coarseTimerAndRepeatedIntervalsAreObservationsWithoutInvalidation() {
        var coarse=Collections.nCopies(40,100);
        assertThat(TapValidation.inspect(coarse,List.of(),10000).status()).isEqualTo("VALID");
        assertThat(TapSignals.inspect(coarse).codes()).containsExactly("SAME_TIMESTAMP");
        assertThat(TapSignals.inspect(coarse).sameTimestampPairs()).isEqualTo(39);
        var regular=IntStream.range(0,300).map(i->i*25).boxed().toList();
        assertThat(TapValidation.inspect(regular,List.of(),10000).status()).isEqualTo("VALID");
        assertThat(TapSignals.inspect(regular).codes()).containsExactly("REGULAR_INTERVALS","HIGH_VOLUME");
        assertThat(TapSignals.inspect(regular).intervalSharePermille()).isEqualTo(1000);
    }
    @Test void shortAndIrregularHumanInputIsNotCalledAutomation() {
        assertThat(TapSignals.inspect(List.of()).codes()).isEmpty();
        assertThat(TapSignals.inspect(List.of(100,100,201,390,521,700,905)).codes()).isEmpty();
        assertThat(TapSignals.inspect(List.of(100)).intervalSharePermille()).isZero();
        assertThat(TapValidation.inspect(List.of(100,100),List.of(new TapValidation.Interruption("HIDDEN",200)),10000).status()).isEqualTo("INTERRUPTED");
    }
}
