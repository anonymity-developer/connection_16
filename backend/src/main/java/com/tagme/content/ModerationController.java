package com.tagme.content;

import com.tagme.auth.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import static com.tagme.content.ContentModels.*;

@RestController @RequestMapping("/api/v1/admin/moderation")
public class ModerationController {
    public record DecisionRequest(@NotNull @Min(0) Long expectedFence,@NotNull @Pattern(regexp="APPROVED|REJECTED") String decision,@NotBlank @Size(max=300) String reason) { }
    private final ModerationService service;
    public ModerationController(ModerationService service) { this.service=service; }
    private AuthPrincipal actor(Authentication auth) { if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p; }
    @GetMapping public List<ReviewTask> queue(Authentication auth) { return service.queue(actor(auth)); }
    @PostMapping("/{jobId}/decision") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(Authentication auth,@PathVariable @Positive long jobId,@Valid @RequestBody DecisionRequest body) { service.review(actor(auth),jobId,new ReviewRequest(body.expectedFence(),body.decision(),body.reason())); }
}
