package com.tagme.participation;

import org.springframework.stereotype.Component;
import static com.tagme.participation.ParticipationModels.*;

@Component
public class ShowMeCompletionRule implements CompletionRule {
    private final ParticipationRepository repository;
    public ShowMeCompletionRule(ParticipationRepository repository) { this.repository = repository; }
    public MissionCode code() { return MissionCode.SHOW_ME; }
    public boolean accepts(long userId, Mission mission, Receipt receipt) {
        var profile = repository.profile(userId);
        return repository.showMeEligible(userId) && "PROFILE".equals(receipt.sourceKind()) && receipt.sourceId() == profile.version();
    }
    public java.time.Instant conditionReceivedAt(long userId, Receipt receipt) {
        var profile = repository.profile(userId);
        var profileReceipt = repository.profileReceipt(userId,profile.version());
        return profileReceipt == null ? receipt.receivedAt() : profileReceipt.receivedAt();
    }
}
