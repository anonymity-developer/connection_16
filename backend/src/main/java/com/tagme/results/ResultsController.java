package com.tagme.results;

import com.tagme.auth.AuthPrincipal;
import com.tagme.auth.AuthException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class ResultsController {
    public record SyncRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,64}") String questionnaireVersion,
                              @NotEmpty @Size(max=20) List<@NotNull @Valid Answer> answers) { }
    public record Answer(@NotBlank @Size(max=32) String questionId,@NotBlank @Size(max=32) String optionId) { }
    public record CancelRequest(@Positive long attemptId) { }
    public record FinishRequest(@Positive long attemptId,@NotNull @Size(max=1000) List<@NotNull @Min(0) @Max(9999) Integer> taps,
                                @NotNull @Size(max=20) List<@NotNull @Valid Interruption> interruptions) { }
    public record Interruption(@NotNull @Pattern(regexp="HIDDEN|PAGE_HIDE") String type,@Min(-5000) @Max(15000) int atMs) { }
    public record QuestionOption(String id,String label) { }
    public record Question(String id,String text,List<QuestionOption> options) { }
    public record Questions(boolean available,String version,String title,List<Question> questions) { }
    public record SyncResult(String questionnaireVersion,Questionnaire.Type type,java.util.Map<String,Integer> axisScores,Instant submittedAt) { }
    public record SyncState(SyncResult result) { }
    public record TapAttempt(long attemptId,int generation,String status,Instant serverNow,Instant startsAt,Instant endsAt,Instant submitDeadline,Integer tapCount,String reviewReason,boolean provisional) { }
    public record TapState(boolean officialAvailable,TapAttempt attempt) { }
    public record RecordMessage(@Positive long attemptId,@NotBlank @Size(max=100) String content) { }
    private final ResultsService service;
    private final TapRankingService rankings;
    public ResultsController(ResultsService service,TapRankingService rankings){this.service=service;this.rankings=rankings;}
    @GetMapping("/sync/questions") public Questions questions(Authentication auth){return service.questions(actor(auth));}
    @GetMapping("/sync/me") public SyncState sync(Authentication auth){return new SyncState(service.sync(actor(auth).userId()));}
    @GetMapping("/sync/types") public List<Questionnaire.Type> types(Authentication auth){return service.types(actor(auth));}
    @PostMapping("/sync/submit") public SyncResult submit(Authentication auth,@Valid @RequestBody SyncRequest body,@RequestHeader(value="X-Participation-Generation",defaultValue="0") long generation){return service.submit(actor(auth),body,generation);}
    @GetMapping("/tap/me") public TapState tap(Authentication auth){return service.tap(actor(auth).userId());}
    @GetMapping("/tap/leaderboard") public TapRankingService.Board leaderboard(Authentication auth){return rankings.board(actor(auth));}
    @PostMapping("/tap/record-message") public TapRankingService.Board message(Authentication auth,@Valid @RequestBody RecordMessage body,@RequestHeader("Idempotency-Key") String key,@RequestHeader(value="X-Participation-Generation",defaultValue="0") long generation){return rankings.message(actor(auth),body.attemptId(),body.content(),key,generation);}
    @PostMapping("/tap/start") public TapAttempt start(Authentication auth,@RequestHeader("Idempotency-Key") String key,@RequestHeader(value="X-Participation-Generation",defaultValue="0") long generation){return service.start(actor(auth),key,generation);}
    @PostMapping("/tap/cancel") public TapAttempt cancel(Authentication auth,@Valid @RequestBody CancelRequest body,@RequestHeader(value="X-Participation-Generation",defaultValue="0") long generation){return service.cancel(actor(auth),body.attemptId(),generation);}
    @PostMapping("/tap/finish") public TapAttempt finish(Authentication auth,@Valid @RequestBody FinishRequest body,@RequestHeader(value="X-Participation-Generation",defaultValue="0") long generation){return service.finish(actor(auth),body,generation);}
    private AuthPrincipal actor(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p;}
}
