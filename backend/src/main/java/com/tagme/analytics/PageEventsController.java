package com.tagme.analytics;

import com.tagme.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics/events")
public class PageEventsController {
    public record Event(@NotNull @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String eventKey,
        @NotNull @Pattern(regexp="[A-Z_]{1,40}") String screen,
        @NotNull @Pattern(regexp="PAGE_VIEW|MISSION_STEP_VIEW") String type,
        @Pattern(regexp="[A-Z_]{1,40}") String step,
        @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String parentPageKey) {
        public Event { if(eventKey!=null)eventKey=eventKey.toLowerCase(java.util.Locale.ROOT);if(parentPageKey!=null)parentPageKey=parentPageKey.toLowerCase(java.util.Locale.ROOT); }
    }
    private final PageEventService service;private final TelemetrySessions sessions;
    public PageEventsController(PageEventService service,TelemetrySessions sessions) { this.service=service;this.sessions=sessions; }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void collect(Authentication authentication,@Valid @RequestBody Event event,@RequestHeader(value="X-Participation-Generation",defaultValue="0") long generation,HttpServletRequest request) {
        if(authentication==null || !(authentication.getPrincipal() instanceof AuthPrincipal actor)) throw AuthException.invalid();
        service.collect(actor,event,generation,sessions.key(request,actor.userId()));
    }
}
