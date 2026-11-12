package com.tagme.participation;

import java.util.List;
import com.tagme.participation.ParticipationModels.CatalogInterest;

public final class InterestCatalog {
    public static final List<String> CATEGORIES=List.of("INDUSTRY","DEVELOPMENT");
    private InterestCatalog() { }
    public static boolean complete(List<Long> selected,List<CatalogInterest> catalog) {
        return CATEGORIES.stream().allMatch(category->{
            long count=catalog.stream().filter(i->category.equals(i.category())&&selected.contains(i.id())).count();
            return count>=1&&count<=3;
        });
    }
    /** Column is repository-owned, never a request value. Retired selections do not satisfy the gate. */
    public static String readySql(String column) {
        return CATEGORIES.stream().map(category->"(SELECT COUNT(*) FROM user_interests ready_ui JOIN interests ready_i ON ready_i.id=ready_ui.interest_id WHERE ready_ui.user_id="
            +column+" AND ready_i.active=TRUE AND ready_i.category='"+category+"') BETWEEN 1 AND "+3)
            .collect(java.util.stream.Collectors.joining(" AND "));
    }
}
