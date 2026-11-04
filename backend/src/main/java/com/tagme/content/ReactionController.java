package com.tagme.content;

import com.tagme.auth.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import static com.tagme.content.ReactionRepository.*;

@RestController @RequestMapping("/api/v1")
public class ReactionController {
    public record CommentRequest(@NotBlank @Size(max=200) String content,@NotNull Boolean revealProfile) { }
    private final ReactionService service;
    public ReactionController(ReactionService service) { this.service=service; }
    private AuthPrincipal actor(Authentication auth) { if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p; }
    @GetMapping("/wishes/{id}/comments") public Comments comments(@PathVariable long id,@RequestParam(required=false) Long cursor) { return service.comments(id,cursor); }
    @GetMapping("/wishes/{id}/comments/me") public OwnComments own(Authentication auth,@PathVariable long id,@RequestParam(required=false) Long cursor) { return service.own(actor(auth).userId(),id,cursor); }
    @PostMapping("/wishes/{id}/comments") @ResponseStatus(HttpStatus.ACCEPTED)
    public OwnComment submit(Authentication auth,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CommentRequest body) { return service.submit(actor(auth),id,key,body); }
    @GetMapping("/cheers/{id}/like") public LikeState state(Authentication auth,@PathVariable long id) { return service.state(actor(auth),id); }
    @PutMapping("/cheers/{id}/like") public LikeState like(Authentication auth,@PathVariable long id) { return service.like(actor(auth),id,true); }
    @DeleteMapping("/cheers/{id}/like") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(Authentication auth,@PathVariable long id) { service.like(actor(auth),id,false); }
}
