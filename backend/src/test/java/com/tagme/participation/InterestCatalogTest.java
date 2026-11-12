package com.tagme.participation;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InterestCatalogTest {
    private final List<ParticipationModels.CatalogInterest> catalog=IntStream.rangeClosed(1,8)
        .mapToObj(id->new ParticipationModels.CatalogInterest(id,"test-"+id,id<=4?"INDUSTRY":"DEVELOPMENT",null)).toList();
    @Test void everyGroupNeedsItsOwnSelection(){assertThat(InterestCatalog.complete(List.of(1L,5L),catalog)).isTrue();assertThat(InterestCatalog.complete(List.of(1L),catalog)).isFalse();}
    @Test void groupMaximumsDoNotBorrowCapacityFromEachOther(){assertThat(InterestCatalog.complete(List.of(1L,2L,3L,4L,5L),catalog)).isFalse();assertThat(InterestCatalog.complete(List.of(1L,5L,6L,7L,8L),catalog)).isFalse();}
}
