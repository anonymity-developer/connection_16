package com.tagme.discovery;

import java.util.ArrayList;
import java.util.List;

/** Preserves weighted scores while favoring new faces and a more varied recommendation list. */
final class RecommendationReranker {
    static final int SHORTLIST_LIMIT = RecommendationPolicy.LIMIT * 4;
    static final int DIFFERENT_CLASS_TARGET = 6;
    static final int UNVISITED_TARGET = 8;
    static final int DIVERSITY_BONUS = 300; // Three points; ranking scores use hundredths.

    record Candidate(long id, int score, boolean unvisited, boolean differentClass, boolean recentlyExposed) { }

    private RecommendationReranker() { }

    static List<Long> select(List<Candidate> ranked) {
        var remaining = new ArrayList<>(ranked);
        var chosen = new ArrayList<Long>(Math.min(RecommendationPolicy.LIMIT, remaining.size()));
        int differentClass = 0;
        int unvisited = 0;
        while (chosen.size() < RecommendationPolicy.LIMIT && !remaining.isEmpty()) {
            Candidate best = null;
            int bestMerit = Integer.MIN_VALUE;
            for (var candidate : remaining) {
                int merit = candidate.score()
                    + (candidate.differentClass() && differentClass < DIFFERENT_CLASS_TARGET ? DIVERSITY_BONUS : 0)
                    + (candidate.unvisited() && unvisited < UNVISITED_TARGET ? DIVERSITY_BONUS : 0);
                // Preserve the SQL order (including the seeded hash) when adjusted merits tie.
                if (best == null || (best.recentlyExposed() && !candidate.recentlyExposed())
                    || (best.recentlyExposed() == candidate.recentlyExposed() && merit > bestMerit)) {
                    best = candidate;
                    bestMerit = merit;
                }
            }
            chosen.add(best.id());
            if (best.differentClass()) differentClass++;
            if (best.unvisited()) unvisited++;
            remaining.remove(best);
        }
        return chosen;
    }
}
