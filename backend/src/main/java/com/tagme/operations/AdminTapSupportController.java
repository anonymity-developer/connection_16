package com.tagme.operations;

import com.tagme.auth.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/tap-attempts")
public class AdminTapSupportController {
    private final AdminTapSupportService service;
    private final TapRecoveryService recovery;
    public AdminTapSupportController(AdminTapSupportService service,TapRecoveryService recovery){this.service=service;this.recovery=recovery;}
    private AuthPrincipal actor(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p;}
    @GetMapping public AdminAnalytics.Page<AdminTapSupportService.Attempt> list(Authentication auth,
        @RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit,
        @RequestParam(defaultValue="ALL") String filter,@RequestParam(required=false) String q) {
        return service.list(actor(auth),offset,limit,filter,q);
    }
    @PostMapping("/members/{id}/retry") public ParticipationResetService.ResetRecord retry(Authentication auth,
        @PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody AdminTapSupportService.RetryRequest body) {
        return service.retry(actor(auth),id,key,body);
    }
    @PostMapping("/members/{id}/restore") public TapRecoveryService.Recovery restore(Authentication auth,
        @PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody TapRecoveryService.Request body) {
        return recovery.restore(actor(auth),id,key,body);
    }
}
