package com.tagme.results;
import org.springframework.stereotype.Component;
import com.tagme.participation.*;
import static com.tagme.participation.ParticipationModels.*;
@Component
public class SyncCompletionRule implements CompletionRule {
    private final ResultsRepository repository;
    public SyncCompletionRule(ResultsRepository repository){this.repository=repository;}
    public MissionCode code(){return MissionCode.SYNC_ME;}
    public boolean accepts(long userId,Mission mission,Receipt receipt){return "SYNC_RESULT".equals(receipt.sourceKind())&&receipt.sourceId()==userId&&repository.sync(userId)!=null;}
}
