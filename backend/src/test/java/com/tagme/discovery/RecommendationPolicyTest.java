package com.tagme.discovery;
import org.junit.jupiter.api.Test;
import static com.tagme.discovery.RecommendationPolicy.*;
import static org.assertj.core.api.Assertions.*;
class RecommendationPolicyTest {
    static final Similarity FULL=new Similarity(1,1,1),EMPTY=new Similarity(0,0,0);
    Signals signals(boolean visited,int exposures){return new Signals(FULL,FULL,true,true,true,visited,true,exposures);}
    @Test void completeSignalsHaveOneHundredPoints(){assertThat(score(signals(false,0))).isEqualTo(10000);}
    @Test void eachCategoryHasFifteenPointsRegardlessOfItsCatalogSize(){assertThat(score(new Signals(FULL,EMPTY,false,false,false,true,false,0))).isEqualTo(1500);assertThat(score(new Signals(EMPTY,FULL,false,false,false,true,false,0))).isEqualTo(1500);}
    @Test void emptyInterestsInventNoAffinity(){assertThat(score(new Signals(EMPTY,EMPTY,false,false,false,true,false,0))).isZero();}
    @Test void similarityUsesIntersectionOverUnion(){assertThat(new Similarity(2,3,3).points()).isEqualTo(750);assertThat(new Similarity(2,3,10).points()).isEqualTo(272);}
    @Test void exposurePenaltyIsBoundedAtTwentyPointsAndAppliesOnlyBeforeAVisit(){assertThat(score(signals(false,1))).isEqualTo(9500);assertThat(score(signals(false,20))).isEqualTo(8000);assertThat(score(signals(true,20))).isEqualTo(score(signals(true,0)));}
    @Test void anUnvisitedPeerCanOutweighAVisitedCommentConnection(){assertThat(score(new Signals(EMPTY,EMPTY,false,false,false,false,false,0))).isGreaterThan(score(new Signals(EMPTY,EMPTY,false,true,false,true,false,0)));}
    @Test void impossibleCountsAreRejected(){assertThatThrownBy(()->new Similarity(2,1,1)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->signals(false,-1)).isInstanceOf(IllegalArgumentException.class);}
}
