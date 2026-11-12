package com.tagme.content;

import com.tagme.auth.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import static com.tagme.content.ContentModels.*;

@RestController @RequestMapping("/api/v1")
public class ContentController {
    public record Submit(@NotBlank @Size(max=400) String content,@NotNull @Pattern(regexp="PLAIN|NOTE") String templateId,
                         @NotNull @Pattern(regexp="ROSE|INK") String style,@NotNull Boolean anonymous,@Positive Long expectedVersion) { }
    private final ContentService service;
    public ContentController(ContentService service) { this.service=service; }
    private AuthPrincipal actor(Authentication auth) { if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p; }
    @GetMapping("/wishes/me") public OwnSlot wish(Authentication auth) { return service.own(actor(auth).userId(),Kind.WISH); }
    @GetMapping("/cheers/me") public OwnSlot cheer(Authentication auth) { return service.own(actor(auth).userId(),Kind.CHEER); }
    @GetMapping("/wishes") public Feed wishes(@RequestParam(required=false) Long cursor) { return service.feed(Kind.WISH,cursor); }
    @GetMapping("/cheers") public Feed cheers(@RequestParam(required=false) Long cursor) { return service.feed(Kind.CHEER,cursor); }
    @PostMapping("/wishes") @ResponseStatus(HttpStatus.ACCEPTED)
    public OwnSlot addWish(Authentication auth,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Submit body,@RequestHeader(value="X-Participation-Generation",defaultValue="0") long generation) { return service.submit(actor(auth),Kind.WISH,key,body,false,generation); }
    @PostMapping("/cheers") @ResponseStatus(HttpStatus.ACCEPTED)
    public OwnSlot addCheer(Authentication auth,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Submit body,@RequestHeader(value="X-Participation-Generation",defaultValue="0") long generation) { return service.submit(actor(auth),Kind.CHEER,key,body,false,generation); }
    @PutMapping("/wishes/me") @ResponseStatus(HttpStatus.ACCEPTED)
    public OwnSlot editWish(Authentication auth,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Submit body,@RequestHeader(value="X-Participation-Generation",defaultValue="0") long generation) { return service.submit(actor(auth),Kind.WISH,key,body,true,generation); }
    @PutMapping("/cheers/me") @ResponseStatus(HttpStatus.ACCEPTED)
    public OwnSlot editCheer(Authentication auth,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Submit body,@RequestHeader(value="X-Participation-Generation",defaultValue="0") long generation) { return service.submit(actor(auth),Kind.CHEER,key,body,true,generation); }
}
