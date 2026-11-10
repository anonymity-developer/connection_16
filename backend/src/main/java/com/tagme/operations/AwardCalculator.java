package com.tagme.operations;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static com.tagme.operations.AwardModels.*;

/** Pure ranking of validated DB facts; never assigns a prize or changes a completion. */
public final class AwardCalculator {
    private AwardCalculator() { }
    public static final String RULE_VERSION="tagme-candidates-v1-profile-tie";
    private static final Set<String> MISSIONS=Set.of("SHOW_ME","SYNC_ME","MAKE_A_WISH","FAST_TAP","SAY_SOMETHING");
    private record Entry(User user,Instant completedAt,List<Proof> proofs) { }
    public static Ranking rank(Facts facts) {
        var byUser=facts.proofs().stream().collect(Collectors.groupingBy(Proof::userId));
        var entries=new ArrayList<Entry>(); long incomplete=0;
        for(var user:facts.users()) {
            var proofs=byUser.getOrDefault(user.id(),List.of());
            // Require exactly one valid proof for each mission. No inference from completion_order.
            if(user.profileRegisteredAt()==null || proofs.size()!=5 || proofs.stream().anyMatch(p->!p.valid()||p.acceptedAt()==null)
                    || !proofs.stream().map(Proof::mission).collect(Collectors.toSet()).equals(MISSIONS)) { if(!proofs.isEmpty())incomplete++;continue; }
            var ordered=proofs.stream().sorted(Comparator.comparing(Proof::mission)).toList();
            entries.add(new Entry(user,ordered.stream().map(Proof::acceptedAt).max(Comparator.naturalOrder()).orElseThrow(),ordered));
        }
        var comments=counts(facts.comments());var hearts=counts(facts.hearts());var taps=counts(facts.taps());
        Map<Category,List<Candidate>> result=new EnumMap<>(Category.class);
        for(var category:Category.values()) {
            var eligible=entries.stream().filter(e->category!=Category.FAST_TAP||taps.containsKey(e.user().id())).toList();
            Comparator<Entry> metric=category==Category.FIRST_COMPLETION?Comparator.comparing(Entry::completedAt)
                    :Comparator.<Entry>comparingLong(e->score(category,e.user().id(),comments,hearts,taps)).reversed();
            var rankOrder=metric.thenComparing(e->e.user().profileRegisteredAt());
            var sorted=eligible.stream().sorted(rankOrder.thenComparingLong(e->e.user().id())).toList();
            var rows=new ArrayList<Candidate>(); int rank=0;
            for(int i=0;i<sorted.size();i++) {
                var e=sorted.get(i);if(i==0||rankOrder.compare(sorted.get(i-1),e)!=0)rank=i+1;
                boolean tied=(i>0&&metric.compare(sorted.get(i-1),e)==0)||(i+1<sorted.size()&&metric.compare(sorted.get(i+1),e)==0);
                boolean unresolved=(i>0&&rankOrder.compare(sorted.get(i-1),e)==0)||(i+1<sorted.size()&&rankOrder.compare(sorted.get(i+1),e)==0);
                rows.add(new Candidate(rank,e.user().id(),e.user().nickname(),e.user().profileRegisteredAt(),e.completedAt(),
                        category==Category.FIRST_COMPLETION?null:score(category,e.user().id(),comments,hearts,taps),tied,unresolved,List.of(),e.proofs()));
            }
            result.put(category,List.copyOf(rows));
        }
        Map<Long,List<Category>> tops=new HashMap<>();
        result.forEach((category,rows)->rows.stream().filter(r->r.rank()==1).forEach(r->tops.computeIfAbsent(r.userId(),k->new ArrayList<>()).add(category)));
        result.replaceAll((category,rows)->rows.stream().map(r->{var categories=tops.getOrDefault(r.userId(),List.of());return new Candidate(r.rank(),r.userId(),r.nickname(),r.profileRegisteredAt(),r.validCompletionAt(),r.score(),r.metricTie(),r.unresolvedTie(),categories.size()>1?List.copyOf(categories):List.of(),r.proofs());}).toList());
        return new Ranking(Collections.unmodifiableMap(result),entries.size(),facts.proofs().stream().filter(p->!p.valid()).count(),incomplete);
    }
    private static Map<Long,Long> counts(List<Activity> rows) { return rows.stream().collect(Collectors.toMap(Activity::userId,Activity::count,Math::max)); }
    private static long score(Category c,long id,Map<Long,Long> comments,Map<Long,Long> hearts,Map<Long,Long> taps) {
        return (c==Category.COMMENT_KING?comments:c==Category.POPULAR_CHEER?hearts:taps).getOrDefault(id,0L);
    }
}
