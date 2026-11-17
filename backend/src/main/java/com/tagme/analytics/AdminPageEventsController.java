package com.tagme.analytics;

import com.tagme.auth.*;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminPageEventsController {
    private final AdminPageEvents service;
    public AdminPageEventsController(AdminPageEvents service){this.service=service;}
    @GetMapping public AdminPageEvents.Log log(Authentication auth,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
        @RequestParam(required=false) String screen,@RequestParam(required=false) String type,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="50") int limit){
        return service.log(actor(auth),AdminPageEvents.window(from,to),screen,type,offset,limit);
    }
    @GetMapping("/summary") public AdminPageEvents.Summary summary(Authentication auth,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){
        return service.summary(actor(auth),AdminPageEvents.window(from,to));
    }
    private static AuthPrincipal actor(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p;}
}
