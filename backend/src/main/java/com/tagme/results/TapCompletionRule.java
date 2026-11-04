package com.tagme.results;
import org.springframework.stereotype.Component;
import com.tagme.participation.*;
import static com.tagme.participation.ParticipationModels.*;
@Component
public class TapCompletionRule implements CompletionRule {
    private final ResultsRepository repository;
    public TapCompletionRule(ResultsRepository repository){this.repository=repository;}
    public MissionCode code(){return MissionCode.FAST_TAP;}
    public boolean accepts(long userId,Mission mission,Receipt receipt){var attempt=repository.attempt(userId,receipt.sourceId());return "GAME_ATTEMPT".equals(receipt.sourceKind())&&attempt!=null&&"VALID".equals(attempt.status())&&attempt.hash()!=null;}
}
