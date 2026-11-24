package com.tagme.analytics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FunnelServiceTest {
    @Test void independentCountsExposeMissingEventsWithoutClamping() {
        var rows=FunnelService.steps(new long[]{10,8,9,4});
        assertThat(rows.get(0).dropOffRate()).isNull();
        assertThat(rows.get(1).dropOffRate()).isEqualTo(20.0);
        assertThat(rows.get(2).dropOffRate()).isEqualTo(-12.5);
        assertThat(rows.get(3).conversionRate()).isEqualTo(40.0);
        assertThat(rows.get(3).eventType()).isEqualTo("SUBMIT_SUCCESS");
    }
    @Test void zeroDenominatorsAreMissingRatherThanInventedRates() {
        var rows=FunnelService.steps(new long[]{0,0,2});
        assertThat(rows).allSatisfy(row -> assertThat(row.conversionRate()).isNull());
        assertThat(rows.get(2).dropOffRate()).isNull();
    }
}
