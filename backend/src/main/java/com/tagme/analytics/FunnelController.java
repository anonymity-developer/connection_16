package com.tagme.analytics;

import com.tagme.auth.AuthException;
import com.tagme.auth.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class FunnelController {
    public record Event(
        @NotNull @Pattern(regexp="[0-9a-fA-F-]{36}") String eventKey,
        @NotNull @Pattern(regexp="SYNC_ME") String mission,
        @NotNull @Pattern(regexp="SCREEN_ENTER|QUESTION_VIEW") String eventType,
        @NotNull @Pattern(regexp="[A-Za-z0-9._-]{1,64}") String questionnaireVersion,
        @Min(0) @Max(20) int step,
        @NotNull Instant occurredAt) {}

    private final FunnelService service;
    public FunnelController(FunnelService service) { this.service = service; }

    @PostMapping("/sync/funnel-events") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void collect(Authentication auth, @Valid @RequestBody Event event,
            @RequestHeader(value="X-Participation-Generation", defaultValue="0") long generation) {
        service.collect(actor(auth), event, generation);
    }

    @GetMapping("/admin/funnel")
    public FunnelService.Report report(Authentication auth) { return service.report(actor(auth)); }

    private AuthPrincipal actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) throw AuthException.invalid();
        return p;
    }
}
