package com.tagme.participation;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InterestCatalogTest {
    private final List<ParticipationModels.CatalogInterest> catalog=IntStream.rangeClosed(1,30)
        .mapToObj(id->new ParticipationModels.CatalogInterest(id,"test-"+id,id<=4?"INDUSTRY":id<=8?"DEVELOPMENT":"TECH_STACK",null)).toList();
    @Test void everyGroupNeedsItsOwnSelection(){assertThat(InterestCatalog.complete(List.of(1L,5L,9L),catalog)).isTrue();assertThat(InterestCatalog.complete(List.of(1L,5L),catalog)).isFalse();}
    @Test void groupMaximumsDoNotBorrowCapacityFromEachOther(){assertThat(InterestCatalog.complete(List.of(1L,2L,3L,4L,5L,9L),catalog)).isFalse();assertThat(InterestCatalog.complete(java.util.stream.LongStream.concat(java.util.stream.LongStream.of(1,5),java.util.stream.LongStream.rangeClosed(9,19)).boxed().toList(),catalog)).isFalse();}
}
