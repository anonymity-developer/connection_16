package com.tagme.discovery;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RelationshipWeightTest {
    @Test void reciprocalVisitsHaveOneBonusAndLikesAreCurrentState() {
        assertThat(RelationshipWeight.score(true,false,false)).isEqualTo(1);
        assertThat(RelationshipWeight.score(false,true,false)).isEqualTo(1);
        assertThat(RelationshipWeight.score(true,true,false)).isEqualTo(4);
        assertThat(RelationshipWeight.score(true,true,true)).isEqualTo(6);
        // Unlike/re-like recomputes the same score; no historical counter is involved.
        assertThat(RelationshipWeight.score(true,false,true)).isEqualTo(3);
        assertThat(RelationshipWeight.score(true,false,false)).isEqualTo(1);
        assertThat(RelationshipWeight.score(true,false,true)).isEqualTo(3);
    }
}
