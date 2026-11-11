package com.tagme.results;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ResultRulesTest {
    static Questionnaire fixture(){return new Questionnaire("테스트 전용 질문",List.of(new Questionnaire.Axis("social","I","E","NEGATIVE")),
        List.of(new Questionnaire.Question("q1","테스트 선택",List.of(new Questionnaire.Option("a","선택 A",Map.of("social",-1)),new Questionnaire.Option("b","선택 B",Map.of("social",1))))),
        List.of(new Questionnaire.Type("I","테스트 I","테스트 설명"),new Questionnaire.Type("E","테스트 E","테스트 설명")));}
    @Test void scoresAreComputedFromValidSelectionsAndDuplicatesAreRejected(){var q=fixture();q.validate();assertThat(q.score(List.of(new Questionnaire.Answer("q1","b"))).type().code()).isEqualTo("E");assertThatThrownBy(()->q.score(List.of(new Questionnaire.Answer("q1","unknown")))).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->q.score(List.of())).isInstanceOf(IllegalArgumentException.class);}
    @Test void tieAndEveryTypeCombinationArePartOfThePublishedVersion(){var f=fixture();var q=new Questionnaire(f.title(),f.axes(),List.of(new Questionnaire.Question("q1","테스트 동점",List.of(new Questionnaire.Option("a","A",Map.of("social",0)),new Questionnaire.Option("b","B",Map.of("social",0))))),f.types());q.validate();assertThat(q.score(List.of(new Questionnaire.Answer("q1","a"))).type().code()).isEqualTo("I");assertThatThrownBy(()->new Questionnaire(f.title(),f.axes(),f.questions(),List.of(f.types().getFirst())).validate()).isInstanceOf(IllegalArgumentException.class);}
    @Test void gameEventBoundsAndSuspiciousRatesAreDistinctFromInterruptions(){assertThat(TapValidation.inspect(List.of(0,100,500),List.of(),10000).status()).isEqualTo("VALID");assertThat(TapValidation.inspect(List.of(0,0),List.of(),10000).status()).isEqualTo("REVIEW_REQUIRED");assertThat(TapValidation.inspect(List.of(100),List.of(new TapValidation.Interruption("HIDDEN",200)),10000).status()).isEqualTo("INTERRUPTED");assertThatThrownBy(()->TapValidation.inspect(List.of(10000),List.of(),10000)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->TapValidation.inspect(List.of(2,1),List.of(),10000)).isInstanceOf(IllegalArgumentException.class);}
    @Test void oneMillisecondIntervalsAndMoreThanTwoHundredTapsAreValid() {
        var taps=java.util.stream.IntStream.range(0,1000).boxed().toList();
        var verdict=TapValidation.inspect(taps,List.of(),10000);
        assertThat(verdict.status()).isEqualTo("VALID");assertThat(verdict.count()).isEqualTo(1000);
        assertThat(TapValidation.inspect(List.of(100,101),List.of(),10000).status()).isEqualTo("VALID");
        assertThat(TapValidation.inspect(List.of(100,100),List.of(),10000).status()).isEqualTo("REVIEW_REQUIRED");
        assertThatThrownBy(()->TapValidation.inspect(java.util.stream.IntStream.range(0,1001).boxed().toList(),List.of(),10000)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reviewOnlyQuestionnaireProposalPassesTheSameServerValidator() throws Exception {
        var source=java.nio.file.Files.readString(java.nio.file.Path.of("../docs/sync-questionnaire.proposal.json"));
        var q=Questionnaire.parse(source,new com.fasterxml.jackson.databind.ObjectMapper());
        assertThat(q.questions()).hasSize(6);assertThat(q.types()).hasSize(4);
        for(var type:q.types()) {
            var answers=q.questions().stream().map(question -> new Questionnaire.Answer(question.id(),question.options().stream().max(java.util.Comparator.comparingInt(option -> {
                int total=0;for(int axis=0;axis<q.axes().size();axis++)total+=option.scores().get(q.axes().get(axis).id())*(type.code().charAt(axis)==q.axes().get(axis).positive().charAt(0)?1:-1);return total;
            })).orElseThrow().id())).toList();
            assertThat(q.score(answers).type().code()).isEqualTo(type.code());
        }
    }

}
