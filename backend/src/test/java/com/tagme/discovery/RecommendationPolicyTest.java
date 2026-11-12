package com.tagme.discovery;

import org.junit.jupiter.api.Test;
import static com.tagme.discovery.RecommendationPolicy.*;
import static org.assertj.core.api.Assertions.*;

class RecommendationPolicyTest {
    @Test void completeSignalsHaveOneHundredPoints() {
        assertThat(score(new Signals(3,3,3,true,true,true,false,true))).isEqualTo(10000);
    }
    @Test void emptyProfilesHaveNoInventedAffinity() {
        assertThat(score(new Signals(0,0,0,false,false,false,true,false))).isZero();
        assertThat(score(new Signals(0,0,0,false,false,false,false,true))).isEqualTo(2500);
    }
    @Test void interestsUseIntersectionOverUnionInsteadOfRawMatches() {
        assertThat(score(new Signals(2,3,3,false,false,false,true,false))).isEqualTo(1500);
        assertThat(score(new Signals(2,3,10,false,false,false,true,false))).isEqualTo(545);
        assertThat(score(new Signals(2,10,3,false,false,false,true,false))).isEqualTo(545);
    }
    @Test void affinityCanOutweighAnInteraction() {
        int compatible=score(new Signals(3,3,4,true,false,false,false,true));
        int reacted=score(new Signals(0,3,3,false,false,true,false,true));
        assertThat(compatible).isEqualTo(6750).isGreaterThan(reacted);
        assertThat(reacted).isEqualTo(3500);
    }
    @Test void interactionKindsAddWithoutMultiplyingActivityCounts() {
        assertThat(score(new Signals(0,0,0,false,true,false,true,false))).isEqualTo(1500);
        assertThat(score(new Signals(0,0,0,false,false,true,true,false))).isEqualTo(1000);
        assertThat(score(new Signals(0,0,0,false,true,true,true,false))).isEqualTo(2500);
    }
    @Test void anUnvisitedPeerCanOutweighAPreviouslyVisitedCommentConnection() {
        int newPeer=score(new Signals(0,0,0,false,false,false,false,false));
        int commentPeer=score(new Signals(0,0,0,false,true,false,true,false));
        assertThat(newPeer).isEqualTo(2000).isGreaterThan(commentPeer);
    }
    @Test void impossibleInterestCountsAreRejected() {
        assertThatThrownBy(()->new Signals(-1,1,1,false,false,false,false,false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Signals(2,1,3,false,false,false,false,false)).isInstanceOf(IllegalArgumentException.class);
    }
}
