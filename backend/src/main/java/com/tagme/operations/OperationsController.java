package com.tagme.operations;

import com.tagme.auth.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.tagme.operations.OperationsModels.*;

@RestController @RequestMapping("/api/v1/admin")
public class OperationsController {
    public record Visibility(@Min(1) long expectedVersion,@NotNull @Pattern(regexp="VISIBLE|HIDDEN") String visibility,@NotBlank @Size(max=300) String reason) { }
    public record Retry(@NotNull @Min(0) Long expectedFence,@NotBlank @Size(max=300) String reason) { }
    private final OperationsService service;
    public OperationsController(OperationsService service) { this.service=service; }
    private AuthPrincipal actor(Authentication auth) { if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p; }
    @GetMapping("/access") public Access access(Authentication auth) { return service.access(actor(auth)); }
    @GetMapping("/moderation/tasks") public Page<Task> tasks(Authentication auth,@RequestParam(required=false) Long after,@RequestParam(required=false) Integer limit,@RequestParam(required=false) String status) { return service.tasks(actor(auth),after,limit,status); }
    @GetMapping("/content") public Page<Content> content(Authentication auth,@RequestParam(required=false) Long cursor,@RequestParam(required=false) Integer limit,@RequestParam(required=false) String kind,@RequestParam(required=false) String visibility) { return service.contents(actor(auth),cursor,limit,kind,visibility); }
    @GetMapping("/audit-logs") public Page<Audit> audits(Authentication auth,@RequestParam(required=false) Long cursor,@RequestParam(required=false) Integer limit,@RequestParam(required=false) String action) { return service.audits(actor(auth),cursor,limit,action); }
    @PatchMapping("/content/{id}/visibility") @ResponseStatus(HttpStatus.NO_CONTENT) public void visibility(Authentication auth,@PathVariable @Positive long id,@Valid @RequestBody Visibility body) { service.visibility(actor(auth),id,body.expectedVersion(),body.visibility(),body.reason()); }
    @PostMapping("/moderation/{id}/retry") @ResponseStatus(HttpStatus.NO_CONTENT) public void retry(Authentication auth,@PathVariable @Positive long id,@Valid @RequestBody Retry body) { service.retry(actor(auth),id,body.expectedFence(),body.reason()); }
}
