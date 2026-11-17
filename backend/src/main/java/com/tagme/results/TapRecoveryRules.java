package com.tagme.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** Replays persisted server-received evidence only; absence of evidence is never a zero-score success. */
public final class TapRecoveryRules {
    private TapRecoveryRules() { }
    private record Payload(List<Integer> taps,List<TapValidation.Interruption> interruptions) { }
    public record Decision(boolean recoverable,String reason) { }
    public static Decision inspect(ResultsRepository.Attempt a,ResultsRepository.StoredEvents events,ObjectMapper json) {
        if(a==null||a.archived())return new Decision(false,"NO_CURRENT_ATTEMPT");
        if(!Set.of("INVALID","EXPIRED","REVIEW_REQUIRED").contains(a.status()))return new Decision(false,"NOT_RECLASSIFIABLE");
        if("INVALID".equals(a.status())&&!"DUPLICATE_MILLISECOND".equals(a.reason()))return new Decision(false,"UNSUPPORTED_INVALID_REASON");
        if(a.submittedAt()==null||a.hash()==null||a.count()==null)return new Decision(false,"NO_SUBMITTED_RECORD");
        if(!Duration.between(a.startsAt(),a.endsAt()).equals(Duration.ofSeconds(10))
            ||a.submittedAt().isBefore(a.endsAt())||a.submittedAt().isAfter(a.endsAt().plusSeconds(60)))return new Decision(false,"OUTSIDE_RECOVERY_WINDOW");
        try {
            if(!"VALID".equals(TapValidation.inspect(events.taps(),events.interruptions(),10000).status())||a.count()!=events.taps().size())return new Decision(false,"INVALID_STORED_EVENTS");
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(new Payload(events.taps(),events.interruptions())).getBytes(StandardCharsets.UTF_8)));
            if(!hash.equals(a.hash()))return new Decision(false,"INVALID_STORED_EVENTS");
            return new Decision(true,"RECOVERABLE_RECORD");
        }catch(Exception invalid){return new Decision(false,"INVALID_STORED_EVENTS");}
    }
}
