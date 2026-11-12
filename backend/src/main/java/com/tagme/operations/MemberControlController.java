package com.tagme.operations;

import com.tagme.auth.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/members")
public class MemberControlController {
    private final MemberControlService controls;
    public MemberControlController(MemberControlService controls){this.controls=controls;}
    private AuthPrincipal actor(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p;}
    @PostMapping("/{id}/deactivate") public ParticipationResetService.ResetRecord deactivate(Authentication auth,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody MemberControlService.ChangeRequest body){return controls.change(actor(auth),id,null,key,body);}
    @PostMapping("/{id}/missions/{mission}") public ParticipationResetService.ResetRecord adjust(Authentication auth,@PathVariable long id,@PathVariable String mission,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody MemberControlService.ChangeRequest body){return controls.change(actor(auth),id,mission,key,body);}
}
