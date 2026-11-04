package com.tagme.participation;

import com.tagme.participation.ParticipationModels.*;

/** Server-side domain rules only. No client may select evidence or invoke completion directly. */
public interface CompletionRule {
    MissionCode code();
    boolean accepts(long userId, Mission mission, Receipt receipt);
    default java.time.Instant conditionReceivedAt(long userId, Receipt receipt) { return receipt.receivedAt(); }
}
