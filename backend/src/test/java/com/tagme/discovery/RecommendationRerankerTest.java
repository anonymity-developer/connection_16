package com.tagme.discovery;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecommendationRerankerTest {
    private RecommendationReranker.Candidate candidate(long id,int score,boolean unvisited,boolean differentClass,boolean exposed) {
        return new RecommendationReranker.Candidate(id,score,unvisited,differentClass,exposed);
    }

    @Test void comparableScoresProduceAClassMixWithoutHardQuotas() {
        var rows=new ArrayList<RecommendationReranker.Candidate>();
        for(int i=1;i<=12;i++)rows.add(candidate(i,7000,true,false,false));
        for(int i=13;i<=24;i++)rows.add(candidate(i,6900,true,true,false));
        var chosen=RecommendationReranker.select(rows);
        assertThat(chosen).hasSize(12).doesNotHaveDuplicates();
        assertThat(chosen.stream().filter(id->id>12).count()).isEqualTo(6);
    }

    @Test void comparableScoresFavorUnvisitedPeersUntilTarget() {
        var rows=new ArrayList<RecommendationReranker.Candidate>();
        for(int i=1;i<=12;i++)rows.add(candidate(i,7000,false,false,false));
        for(int i=13;i<=24;i++)rows.add(candidate(i,6900,true,false,false));
        var chosen=RecommendationReranker.select(rows);
        assertThat(chosen.stream().filter(id->id>12).count()).isEqualTo(8);
    }

    @Test void aLargeAffinityGapStillWinsOverTheSoftDiversityBonus() {
        var rows=List.of(candidate(1,9000,false,false,false),candidate(2,6000,true,true,false));
        assertThat(RecommendationReranker.select(rows)).containsExactly(1L,2L);
    }

    @Test void recentExposureDoesNotRepeatWhenEnoughFreshPeersExist() {
        var rows=new ArrayList<RecommendationReranker.Candidate>();
        for(int i=1;i<=12;i++)rows.add(candidate(i,10000,false,false,true));
        for(int i=13;i<=24;i++)rows.add(candidate(i,0,true,true,false));
        var chosen=RecommendationReranker.select(rows);
        assertThat(chosen).hasSize(12).allMatch(id->id>12);
        assertThat(RecommendationReranker.select(rows)).isEqualTo(chosen);
    }

    @Test void recentPeersFillGapsAndEmptyPoolsRemainEmpty() {
        var rows=new ArrayList<RecommendationReranker.Candidate>();
        for(int i=1;i<=12;i++)rows.add(candidate(i,10000,false,false,true));
        for(int i=13;i<=16;i++)rows.add(candidate(i,0,true,true,false));
        var chosen=RecommendationReranker.select(rows);
        assertThat(chosen).hasSize(12).doesNotHaveDuplicates();
        assertThat(chosen.stream().filter(id->id>12).count()).isEqualTo(4);
        assertThat(RecommendationReranker.select(List.of())).isEmpty();
    }
}
