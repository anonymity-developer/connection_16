package com.tagme.operations;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tagme.operations.AwardModels.*;

final class PrizeReviewCalculator {
    private PrizeReviewCalculator() { }

    static PrizeReview calculate(Facts facts, List<PrizePerson> people, List<Activity> visits) {
        var byId = people.stream().collect(Collectors.toMap(PrizePerson::userId, Function.identity()));
        var valid = facts.proofs().stream().filter(Proof::valid)
                .collect(Collectors.groupingBy(Proof::userId));
        var completed = AwardCalculator.rank(facts).categories().get(Category.FIRST_COMPLETION).stream()
                .filter(row -> byId.containsKey(row.userId()))
                .map(row -> new PrizeCandidate(row.rank(), byId.get(row.userId()), null, row.validCompletionAt()))
                .toList();
        var eligibleTap = facts.taps().stream().filter(row -> valid.getOrDefault(row.userId(), List.of())
                .stream().anyMatch(proof -> "FAST_TAP".equals(proof.mission()))).toList();
        var groups = List.of(
                new PrizeGroup("CHICKEN_1", 1, completed),
                new PrizeGroup("CHICKEN_7", 7, completed),
                new PrizeGroup("CHICKEN_77", 77, completed),
                activity("DISCOVERY", visits, byId),
                activity("COMMENTS", facts.comments(), byId),
                activity("FAST_TAP", eligibleTap, byId),
                activity("HEARTS", facts.hearts(), byId)
        );
        var entries = people.stream()
                .map(person -> new DrawEntry(person, (int) valid.getOrDefault(person.userId(), List.of())
                        .stream().map(Proof::mission).distinct().count()))
                .filter(entry -> entry.tickets() > 0)
                .toList();
        return new PrizeReview(facts.window().observedAt(), groups, entries);
    }

    private static PrizeGroup activity(String code, List<Activity> activities, Map<Long, PrizePerson> people) {
        var scores = activities.stream().filter(row -> row.count() > 0 && people.containsKey(row.userId()))
                .sorted(Comparator.comparingLong(Activity::count).reversed().thenComparingLong(Activity::userId))
                .toList();
        var rows = new java.util.ArrayList<PrizeCandidate>();
        int rank = 0;
        for (int index = 0; index < scores.size(); index++) {
            var score = scores.get(index);
            if (index == 0 || scores.get(index - 1).count() != score.count()) rank = index + 1;
            rows.add(new PrizeCandidate(rank, people.get(score.userId()), score.count(), null));
        }
        return new PrizeGroup(code, 1, List.copyOf(rows));
    }
}
