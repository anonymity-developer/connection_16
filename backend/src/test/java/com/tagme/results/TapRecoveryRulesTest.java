package com.tagme.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TapRecoveryRulesTest {
    final ObjectMapper json=new ObjectMapper();
    final ResultsRepository.StoredEvents events=new ResultsRepository.StoredEvents(List.of(100,100,230),List.of());
    ResultsRepository.Attempt attempt(String status,String reason,int delay,String hash,Integer count,boolean archived) {
        var starts=Instant.parse("2026-11-17T01:00:00Z");var ends=starts.plusSeconds(10);
        return new ResultsRepository.Attempt(1,2,1,"original-fixture-key",starts,ends,ends.plusSeconds(5),status,count,ends.plusSeconds(delay),hash,reason,archived);
    }
    String hash()throws Exception {
        var payload=new LinkedHashMap<String,Object>();payload.put("taps",events.taps());payload.put("interruptions",events.interruptions());
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void replaysOnlyCompleteAuthenticRecordsUnderTheNewSixtySecondRule()throws Exception {
        assertThat(TapRecoveryRules.inspect(attempt("INVALID","DUPLICATE_MILLISECOND",1,hash(),3,false),events,json).recoverable()).isTrue();
        assertThat(TapRecoveryRules.inspect(attempt("EXPIRED","SUBMISSION_EXPIRED",60,hash(),3,false),events,json).recoverable()).isTrue();
        assertThat(TapRecoveryRules.inspect(attempt("EXPIRED","SUBMISSION_EXPIRED",61,hash(),3,false),events,json).recoverable()).isFalse();
        assertThat(TapRecoveryRules.inspect(attempt("EXPIRED","SUBMISSION_EXPIRED",-1,hash(),3,false),events,json).recoverable()).isFalse();
        assertThat(TapRecoveryRules.inspect(attempt("INVALID","INVALID_STORED_EVENTS",1,hash(),3,false),events,json).recoverable()).isFalse();
        assertThat(TapRecoveryRules.inspect(attempt("INVALID","DUPLICATE_MILLISECOND",1,"0".repeat(64),3,false),events,json).recoverable()).isFalse();
        assertThat(TapRecoveryRules.inspect(attempt("INVALID","DUPLICATE_MILLISECOND",1,hash(),4,false),events,json).recoverable()).isFalse();
        assertThat(TapRecoveryRules.inspect(attempt("INVALID","DUPLICATE_MILLISECOND",1,hash(),3,true),events,json).recoverable()).isFalse();
        assertThat(TapRecoveryRules.inspect(attempt("EXPIRED","SUBMISSION_EXPIRED",1,null,null,false),events,json).recoverable()).isFalse();
        assertThat(TapRecoveryRules.inspect(attempt("INTERRUPTED","PAGE_INTERRUPTED",1,hash(),3,false),events,json).recoverable()).isFalse();
    }
}
